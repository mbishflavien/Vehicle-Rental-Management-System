import { useEffect, useState } from "react";
import { api, type Branch } from "./api";

// Branches rarely change, so one request is shared by every form on the page.
let cache: Promise<Branch[]> | null = null;

export function useBranches(): Branch[] {
  const [branches, setBranches] = useState<Branch[]>([]);
  useEffect(() => {
    if (!cache) cache = api.branches().catch((e) => { cache = null; throw e; });
    let alive = true;
    cache.then((b) => { if (alive) setBranches(b); }).catch(() => {});
    return () => { alive = false; };
  }, []);
  return branches;
}

export const branchOptions = (branches: Branch[]) => branches.map((b) => ({ value: b.branchId, label: b.name }));

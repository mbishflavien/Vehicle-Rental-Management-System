import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, errorMessage, type Contract, type ContractStatus } from "../api";
import { useCan } from "../auth";
import { Button, DataTable, EmptyState, ErrorBanner, Icon, Loading, RowMenu, StatusPill, TableFooter, useFeedback, usePaged, type MenuAction } from "../components/ui";
import { period, plate, rwf, shortId } from "../format";
import { AdminPageHeader, matches, useAdmin } from "./AdminShell";
import { ContractModal } from "./forms";

const TABS = ["All", "Pending", "Active", "Completed", "Cancelled"] as const;
type Tab = typeof TABS[number];

export default function Contracts() {
  const { query, refreshCounts } = useAdmin();
  const { toast, confirm } = useFeedback();
  const can = useCan();
  const [params, setParams] = useSearchParams();
  const [contracts, setContracts] = useState<Contract[] | null>(null);
  const [error, setError] = useState("");

  const tab: Tab = (TABS as readonly string[]).includes(params.get("tab") ?? "") ? params.get("tab") as Tab : "All";
  const issuing = params.get("issue") === "1";
  const setParam = (key: string, value: string | null) => {
    const p = new URLSearchParams(params);
    if (value) p.set(key, value); else p.delete(key);
    setParams(p, { replace: true });
  };

  const load = () => {
    setError("");
    api.contracts().then(setContracts).catch((e) => setError(errorMessage(e)));
    refreshCounts();
  };
  useEffect(load, []);

  const rows = useMemo(() => (contracts ?? []).filter((c) =>
    (tab === "All" || c.contractStatus === tab.toUpperCase()) &&
    matches(query, shortId("CTR", c.contractId), c.customer.fullName, c.customer.driverLicenseNumber, c.vehicle.model, c.vehicle.plateNumber)),
  [contracts, tab, query]);
  const paged = usePaged(rows, 10);
  const count = (t: Tab) => (contracts ?? []).filter((c) => t === "All" || c.contractStatus === t.toUpperCase()).length;

  const change = async (c: Contract, status: ContractStatus, verb: string, message: React.ReactNode, danger = false) => {
    const ok = await confirm({ title: `${verb}?`, message, confirmLabel: verb, danger });
    if (!ok) return;
    try {
      await api.setContractStatus(c.contractId, status);
      toast(`${shortId("CTR", c.contractId)} ${status === "ACTIVE" ? "approved" : status === "COMPLETED" ? "marked returned" : "cancelled"}`);
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const remove = async (c: Contract) => {
    const open = c.contractStatus === "PENDING" || c.contractStatus === "ACTIVE";
    const ok = await confirm({
      title: "Delete this contract?",
      message: <>The record for <strong>{c.customer.fullName}</strong> ({plate(c.vehicle.plateNumber)}) will be permanently removed.{open && <> The vehicle will be released back to <strong>Available</strong>.</>}</>,
      confirmLabel: "Delete contract",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteContract(c.contractId);
      toast("Contract deleted");
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const actions = (c: Contract): MenuAction[] => {
    const v = <strong>{c.vehicle.model} ({plate(c.vehicle.plateNumber)})</strong>;
    const list: MenuAction[] = [];
    if (c.contractStatus === "PENDING") {
      list.push({ label: "Approve & hand over", onSelect: () => change(c, "ACTIVE", "Approve booking", <>{v} will be marked <strong>Rented</strong> to {c.customer.fullName}.</>) });
    }
    if (c.contractStatus === "ACTIVE") {
      list.push({ label: "Mark returned", onSelect: () => change(c, "COMPLETED", "Mark returned", <>{v} will be marked <strong>Available</strong> again and this contract completed.</>) });
    }
    if (c.contractStatus === "PENDING" || c.contractStatus === "ACTIVE") {
      list.push({ label: "Cancel contract", danger: true, onSelect: () => change(c, "CANCELLED", "Cancel contract", <>The contract will be cancelled and {v} released back to <strong>Available</strong>.</>, true) });
    }
    if (can("CONTRACT_DELETE")) list.push({ label: "Delete record", danger: true, onSelect: () => remove(c) });
    return list;
  };

  return <>
    <AdminPageHeader title="Contracts in motion." description="Track every agreement from booking to return.">
      <div className="segment" role="tablist">{TABS.map((t) => <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? "active" : ""} onClick={() => setParam("tab", t === "All" ? null : t)}>
        {t}{contracts && <em>{count(t)}</em>}
      </button>)}</div>
      <Button onClick={() => setParam("issue", "1")}><Icon name="plus" size={17}/> Issue new contract</Button>
    </AdminPageHeader>
    {error && <ErrorBanner message={error} onRetry={load}/>}
    <section className="admin-card">
      {contracts === null && !error ? <Loading/> : <>
        <DataTable headers={["Contract", "Customer", "Vehicle", "Rental period", "Total cost", "Status", ""]}
          empty={rows.length === 0 && <EmptyState title={tab === "All" && !query ? "No contracts yet" : "No contracts match"} text={tab === "All" && !query ? "Issue a contract or wait for customers to book online." : "Try another tab or search."}/>}>
          {paged.slice.map((c) => <tr key={c.contractId}>
            <td><strong>{shortId("CTR", c.contractId)}</strong><small className="subcell">{c.issuedByName ? `by ${c.issuedByName}` : "Online booking"}</small></td>
            <td><strong>{c.customer.fullName}</strong><small className="subcell">{c.customer.driverLicenseNumber}</small></td>
            <td><strong>{c.vehicle.model}</strong><small className="subcell">{plate(c.vehicle.plateNumber)}</small></td>
            <td>{period(c.startDate, c.endDate)}{c.pickupBranch && <small className="subcell">{c.pickupBranch.name}</small>}</td>
            <td><strong>{rwf(c.totalCost)}</strong></td>
            <td><StatusPill status={c.contractStatus}/></td>
            <td className="actions">{actions(c).length > 0 && <RowMenu actions={actions(c)}/>}</td>
          </tr>)}
        </DataTable>
        <TableFooter shown={paged.slice.length} total={rows.length} noun="contracts" {...paged}/>
      </>}
    </section>
    {issuing && <ContractModal close={() => setParam("issue", null)} issued={() => { setParam("issue", null); load(); }}/>}
  </>;
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import type { Permission } from "../api";
import { useAuth, useCan } from "../auth";
import { ChangePasswordModal } from "../components/account";
import { Icon, Wordmark } from "../components/ui";
import { greeting, initials } from "../format";

interface AdminCtx {
  /** Shared by the top bar search and each page's own search box. */
  query: string;
  setQuery: (q: string) => void;
  pending: number;
  /** Re-reads the pending-bookings count after something changes. */
  refreshCounts: () => void;
}

const Ctx = createContext<AdminCtx | null>(null);
export const useAdmin = () => {
  const c = useContext(Ctx);
  if (!c) throw new Error("useAdmin must be used inside AdminShell");
  return c;
};

const nav: { to: string; label: string; icon: string; end?: boolean; needs?: Permission }[] = [
  { to: "/admin", label: "Dashboard", icon: "dashboard", end: true, needs: "DASHBOARD_READ" },
  { to: "/admin/fleet", label: "Fleet Assets", icon: "car" },
  { to: "/admin/customers", label: "Customer Directory", icon: "users", needs: "CUSTOMER_READ" },
  { to: "/admin/contracts", label: "Rental Contracts", icon: "file", needs: "CONTRACT_READ" },
  { to: "/admin/logs", label: "System Logs", icon: "logs", needs: "AUDIT_READ" },
  { to: "/admin/staff", label: "Staff & Access", icon: "shield", needs: "STAFF_MANAGE" },
];

const titles: Record<string, string> = {
  "/admin/fleet": "Fleet Assets",
  "/admin/customers": "Customer Directory",
  "/admin/contracts": "Rental Contracts",
  "/admin/logs": "System Logs",
  "/admin/staff": "Staff & Access",
};

const topDate = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "long" });

export default function AdminShell() {
  const { user, signOut } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [pending, setPending] = useState(0);
  const [userMenu, setUserMenu] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const can = useCan();

  const refreshCounts = useCallback(() => {
    api.dashboard().then((s) => setPending(s.pendingContracts)).catch(() => {});
  }, []);
  useEffect(refreshCounts, [refreshCounts]);

  // Each page starts with an empty search.
  useEffect(() => { setQuery(""); setUserMenu(false); }, [location.pathname]);

  const isDashboard = location.pathname === "/admin" || location.pathname === "/admin/";
  const title = isDashboard ? `${greeting()}, ${user?.fullName.split(" ")[0]}.` : titles[location.pathname] ?? "VRMS";
  const ctx = useMemo(() => ({ query, setQuery, pending, refreshCounts }), [query, pending, refreshCounts]);

  return <Ctx.Provider value={ctx}>
    <div className="admin-shell">
      <aside className="sidebar">
        <Wordmark admin to="/admin"/>
        <div className="workspace-label">Workspace</div>
        <nav>{nav.filter((n) => !n.needs || can(n.needs)).map((n) => <NavLink key={n.to} to={n.to} end={n.end} title={n.label}>
          <Icon name={n.icon}/><span>{n.label}</span>{n.to === "/admin/contracts" && pending > 0 && <b>{pending}</b>}
        </NavLink>)}</nav>
        <div className="sidebar-spacer"/>
        <NavLink className="back-site" to="/"><Icon name="arrow" size={17}/> View public site</NavLink>
        <div className="user-menu-wrap">
          {userMenu && <div className="row-menu-list user-menu" role="menu">
            <button role="menuitem" onClick={() => { setUserMenu(false); setChangingPassword(true); }}><Icon name="shield" size={16}/> Change password</button>
            <button role="menuitem" onClick={() => { signOut(); navigate("/signin"); }}><Icon name="logout" size={16}/> Sign out</button>
          </div>}
          <button className="user-card" onClick={() => setUserMenu(!userMenu)} aria-expanded={userMenu} aria-label="Account menu">
            <span className="avatar">{initials(user?.fullName ?? "")}</span>
            <div><strong>{user?.fullName}</strong><small>{user?.jobTitle ?? (user?.role === "ADMIN" ? "Administrator" : "Rental agent")}</small></div>
            <Icon name="chevron" size={16}/>
          </button>
        </div>
      </aside>
      <section className="admin-main">
        <header className="topbar">
          <div><h1>{title}</h1>{isDashboard && <span>Here is what's moving today.</span>}</div>
          <div className="top-actions">
            {!isDashboard && <label><Icon name="search" size={18}/><input placeholder="Search this page…" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search this page"/></label>}
            <button className="notify" onClick={() => navigate("/admin/contracts?tab=Pending")} aria-label={pending ? `${pending} bookings waiting for approval` : "No bookings waiting"} title={pending ? `${pending} booking${pending === 1 ? "" : "s"} waiting for approval` : "No bookings waiting"}>
              <Icon name="bell"/>{pending > 0 && <i/>}
            </button>
            <span className="date">{topDate.format(new Date())}</span>
          </div>
        </header>
        <div className="admin-content"><Outlet/></div>
      </section>
    </div>
    {changingPassword && <ChangePasswordModal close={() => setChangingPassword(false)}/>}
  </Ctx.Provider>;
}

export function AdminPageHeader({ title, description, children }: { title: string; description: string; children?: React.ReactNode }) {
  return <div className="admin-page-head"><div><h2>{title}</h2><p>{description}</p></div><div className="admin-page-actions">{children}</div></div>;
}

/** Search box bound to the shared admin query. */
export function AdminSearch({ placeholder }: { placeholder: string }) {
  const { query, setQuery } = useAdmin();
  return <label className="admin-search"><Icon name="search" size={17}/><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={placeholder} aria-label={placeholder}/></label>;
}

/** Case- and space-insensitive match, so "rab 123" finds RAB123A. */
export function matches(query: string, ...fields: (string | null | undefined)[]): boolean {
  const q = query.trim().toLowerCase().replace(/\s+/g, "");
  if (!q) return true;
  return fields.some((f) => f?.toLowerCase().replace(/\s+/g, "").includes(q));
}

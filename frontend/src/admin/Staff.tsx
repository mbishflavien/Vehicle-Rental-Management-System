import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { api, ApiError, errorMessage, type Permission, type Role, type UserView } from "../api";
import { useAuth } from "../auth";
import { Button, DataTable, EmptyState, ErrorBanner, Field, Icon, Loading, RowMenu, Select, StatusPill, useEscape, useFeedback } from "../components/ui";
import { relativeTime } from "../format";
import { AdminPageHeader, AdminSearch, matches, useAdmin } from "./AdminShell";

const ROLE_PERMISSIONS: Record<"ADMIN" | "AGENT", Permission[]> = {
  ADMIN: ["VEHICLE_WRITE", "VEHICLE_DELETE", "CUSTOMER_READ", "CUSTOMER_WRITE", "CUSTOMER_DELETE", "CONTRACT_READ",
    "CONTRACT_WRITE", "CONTRACT_DELETE", "BRANCH_MANAGE", "DASHBOARD_READ", "AUDIT_READ", "NOTIFICATION_READ",
    "DOCUMENT_READ", "STAFF_MANAGE"],
  AGENT: ["VEHICLE_WRITE", "CUSTOMER_READ", "CUSTOMER_WRITE", "CONTRACT_READ", "CONTRACT_WRITE", "DASHBOARD_READ",
    "NOTIFICATION_READ", "DOCUMENT_READ"],
};

const permissionLabel = (p: Permission) => p.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());

function StaffDrawer({ member, close, saved }: { member: UserView | null; close: () => void; saved: () => void }) {
  const { toast } = useFeedback();
  const editing = member !== null;
  const [v, setV] = useState({
    fullName: member?.fullName ?? "", email: member?.email ?? "", jobTitle: member?.jobTitle ?? "",
    role: (member?.role ?? "AGENT") as Role, password: "", enabled: member?.enabled ?? true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  useEscape(useCallback(close, [close]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!v.fullName.trim()) local.fullName = "Full name is required";
    if (!/^\S+@\S+\.\S+$/.test(v.email.trim())) local.email = "Email address is not valid";
    if (!editing && !v.password) local.password = "Set a temporary password";
    if (v.password && (v.password.length < 8 || !/[A-Za-z]/.test(v.password) || !/\d/.test(v.password))) local.password = "At least 8 characters, with letters and numbers";
    setErrors(local);
    if (Object.keys(local).length) return;
    setBusy(true);
    try {
      const body = { fullName: v.fullName.trim(), email: v.email.trim(), jobTitle: v.jobTitle.trim() || null, role: v.role, enabled: v.enabled, password: v.password || undefined };
      const result = editing ? await api.updateStaff(member.userId, body) : await api.createStaff(body);
      toast(editing ? `${result.fullName} updated` : `${result.fullName} can now sign in`);
      saved();
    } catch (err) {
      if (err instanceof ApiError) setErrors(Object.keys(err.fieldErrors).length ? err.fieldErrors : { form: err.message });
    } finally {
      setBusy(false);
    }
  };

  const perms = ROLE_PERMISSIONS[v.role as "ADMIN" | "AGENT"] ?? [];
  return <div className="drawer-overlay" onMouseDown={close}>
    <form className="drawer" role="dialog" aria-modal="true" aria-labelledby="staff-title" onMouseDown={(e) => e.stopPropagation()} onSubmit={submit} noValidate>
      <div className="drawer-head"><div><span className="eyebrow">Staff &amp; access</span><h2 id="staff-title">{editing ? "Edit staff member" : "Add staff member"}</h2><p>{editing ? "Change their role or access." : "They'll sign in with this email and temporary password."}</p></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="drawer-body">
        <Field label="Full name" value={v.fullName} onChange={(e) => setV({ ...v, fullName: e.target.value })} error={errors.fullName} autoFocus/>
        <Field label="Email address" type="email" value={v.email} onChange={(e) => setV({ ...v, email: e.target.value })} error={errors.email} disabled={editing} hint={editing ? "The sign-in email can't be changed." : undefined}/>
        <Field label="Job title" placeholder="e.g. Rental Agent, Airport desk" value={v.jobTitle} onChange={(e) => setV({ ...v, jobTitle: e.target.value })}/>
        <Select label="Role" icon="shield" value={v.role} onChange={(role) => setV({ ...v, role: role as Role })} error={errors.role}
          options={[{ value: "AGENT", label: "Agent: day-to-day rentals" }, { value: "ADMIN", label: "Admin: full access" }]}/>
        <div className="perm-grid" aria-label="Permissions for this role">
          {ROLE_PERMISSIONS.ADMIN.map((p) => <span key={p} className={perms.includes(p) ? "on" : ""}>{perms.includes(p) ? "✓ " : "— "}{permissionLabel(p)}</span>)}
        </div>
        <div style={{ height: 20 }}/>
        <Field label={editing ? "Reset password (optional)" : "Temporary password"} type="password" autoComplete="new-password" value={v.password} onChange={(e) => setV({ ...v, password: e.target.value })} error={errors.password} hint="At least 8 characters, with letters and numbers."/>
        {editing && <label className="field-check"><input type="checkbox" checked={v.enabled} onChange={(e) => setV({ ...v, enabled: e.target.checked })}/> Account active (unticking signs them out everywhere)</label>}
        {errors.form && <p className="form-error">{errors.form}</p>}
      </div>
      <div className="drawer-foot"><Button variant="outline" onClick={close}>Cancel</Button><Button type="submit" busy={busy}>Save <Icon name="arrow" size={17}/></Button></div>
    </form>
  </div>;
}

export default function Staff() {
  const { user } = useAuth();
  const { query } = useAdmin();
  const { toast } = useFeedback();
  const [staff, setStaff] = useState<UserView[] | null>(null);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<UserView | null | undefined>(undefined);

  const load = () => {
    setError("");
    api.staff().then(setStaff).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  const rows = useMemo(() => (staff ?? []).filter((s) => matches(query, s.fullName, s.email, s.jobTitle, s.role)), [staff, query]);

  const toggle = async (s: UserView) => {
    try {
      await api.updateStaff(s.userId, { fullName: s.fullName, email: s.email, jobTitle: s.jobTitle, role: s.role, enabled: !s.enabled });
      toast(`${s.fullName} ${s.enabled ? "disabled" : "re-enabled"}`);
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  return <>
    <AdminPageHeader title="Who can do what." description="Staff accounts and their role-based permissions.">
      <AdminSearch placeholder="Search staff…"/>
      <Button onClick={() => setEditing(null)}><Icon name="plus" size={17}/> Add staff member</Button>
    </AdminPageHeader>
    {error && <ErrorBanner message={error} onRetry={load}/>}
    <section className="admin-card">
      {staff === null && !error ? <Loading/> :
        <DataTable headers={["Name", "Email", "Role", "Last sign-in", "Status", ""]} empty={rows.length === 0 && <EmptyState title="No staff found"/>}>
          {rows.map((s) => <tr key={s.userId}>
            <td><strong>{s.fullName}</strong>{s.userId === user?.userId && <small className="subcell">You</small>}{s.jobTitle && s.userId !== user?.userId && <small className="subcell">{s.jobTitle}</small>}</td>
            <td>{s.email}</td>
            <td><span className={`role-pill ${s.role.toLowerCase()}`}>{s.role === "ADMIN" ? "Admin" : "Agent"}</span></td>
            <td className="muted">{s.lastLoginAt ? relativeTime(s.lastLoginAt) : "Never"}</td>
            <td><StatusPill status={s.enabled ? "ACTIVE" : "CANCELLED"}/></td>
            <td className="actions">{s.userId !== user?.userId && <RowMenu actions={[
              { label: "Edit role & details", onSelect: () => setEditing(s) },
              { label: s.enabled ? "Disable account" : "Enable account", onSelect: () => toggle(s), danger: s.enabled },
            ]}/>}</td>
          </tr>)}
        </DataTable>}
    </section>
    {editing !== undefined && <StaffDrawer member={editing} close={() => setEditing(undefined)} saved={() => { setEditing(undefined); load(); }}/>}
  </>;
}

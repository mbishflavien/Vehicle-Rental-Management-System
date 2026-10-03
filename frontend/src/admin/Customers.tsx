import { useEffect, useMemo, useState } from "react";
import { api, errorMessage, type Customer } from "../api";
import { useCan } from "../auth";
import { Button, DataTable, EmptyState, ErrorBanner, Icon, Loading, RowMenu, TableFooter, useFeedback, usePaged } from "../components/ui";
import { downloadCsv, isoDate, shortId } from "../format";
import { AdminPageHeader, AdminSearch, matches, useAdmin } from "./AdminShell";
import { CustomerDrawer } from "./forms";

export default function Customers() {
  const { query } = useAdmin();
  const { toast, confirm } = useFeedback();
  const can = useCan();
  const [customers, setCustomers] = useState<Customer[] | null>(null);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<Customer | null | undefined>(undefined);

  const load = () => {
    setError("");
    api.customers().then(setCustomers).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  const rows = useMemo(() => (customers ?? []).filter((c) =>
    matches(query, c.fullName, c.email, c.phoneNumber, c.driverLicenseNumber, shortId("CUS", c.customerId))), [customers, query]);
  const paged = usePaged(rows, 10);

  const exportCsv = () => downloadCsv(`vrms-customers-${isoDate(new Date())}.csv`,
    ["Customer ID", "Full name", "Email", "Phone", "Driver license", "Online account", "Registered"],
    rows.map((c) => [shortId("CUS", c.customerId), c.fullName, c.email, c.phoneNumber, c.driverLicenseNumber, c.hasAccount ? "Yes" : "No", c.createdAt?.slice(0, 10)]));

  const remove = async (c: Customer) => {
    const ok = await confirm({
      title: "Delete this customer?",
      message: <><strong>{c.fullName}</strong>, their past contracts{c.hasAccount ? " and their online account" : ""} will be permanently removed. This can't be undone.</>,
      confirmLabel: "Delete customer",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteCustomer(c.customerId);
      toast(`${c.fullName} removed from the directory`);
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  return <>
    <AdminPageHeader title="People behind every journey." description="View and manage your customer directory.">
      <AdminSearch placeholder="Search customers…"/>
      <Button variant="outline" onClick={exportCsv} disabled={!rows.length}><Icon name="download" size={17}/> Export CSV</Button>
      {can("CUSTOMER_WRITE") && <Button onClick={() => setEditing(null)}><Icon name="plus" size={17}/> Register customer</Button>}
    </AdminPageHeader>
    {error && <ErrorBanner message={error} onRetry={load}/>}
    <section className="admin-card">
      {customers === null && !error ? <Loading/> : <>
        <DataTable headers={["Customer ID", "Full name", "Email", "Phone", "Driver license", ""]}
          empty={rows.length === 0 && <EmptyState title={customers?.length ? "No customers match" : "No customers yet"} text={customers?.length ? "Try a name, email or license number." : "Register walk-in customers here. Customers who sign up online appear automatically."}/>}>
          {paged.slice.map((c) => <tr key={c.customerId}>
            <td className="muted">{shortId("CUS", c.customerId)}</td>
            <td><strong>{c.fullName}</strong>{c.hasAccount && <small className="subcell">Online account</small>}</td>
            <td>{c.email}</td>
            <td>{c.phoneNumber || <span className="muted">—</span>}</td>
            <td><span className="license">{c.driverLicenseNumber}</span></td>
            <td className="actions"><RowMenu actions={[
              { label: "Edit profile", onSelect: () => setEditing(c) },
              ...(can("CUSTOMER_DELETE") ? [{ label: "Delete customer", onSelect: () => remove(c), danger: true }] : []),
            ]}/></td>
          </tr>)}
        </DataTable>
        <TableFooter shown={paged.slice.length} total={rows.length} noun="customers" {...paged}/>
      </>}
    </section>
    {editing !== undefined && <CustomerDrawer customer={editing} close={() => setEditing(undefined)} saved={() => { setEditing(undefined); load(); }}/>}
  </>;
}

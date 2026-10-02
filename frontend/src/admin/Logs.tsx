import { useEffect, useMemo, useState } from "react";
import { api, errorMessage, type AuditLog } from "../api";
import { Button, DataTable, EmptyState, ErrorBanner, Icon, Loading, TableFooter, usePaged } from "../components/ui";
import { downloadCsv, isoDate, logTime } from "../format";
import { AdminPageHeader, AdminSearch, matches, useAdmin } from "./AdminShell";

export default function Logs() {
  const { query } = useAdmin();
  const [logs, setLogs] = useState<AuditLog[] | null>(null);
  const [error, setError] = useState("");

  const load = () => {
    setError("");
    api.logs(1000).then(setLogs).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  const rows = useMemo(() => (logs ?? []).filter((l) => matches(query, l.event, l.actor, l.details)), [logs, query]);
  const paged = usePaged(rows, 15);

  const exportCsv = () => downloadCsv(`vrms-logs-${isoDate(new Date())}.csv`, ["Timestamp", "Event", "Actor", "Details"],
    rows.map((l) => [l.timestamp, l.event, l.actor, l.details]));

  return <>
    <AdminPageHeader title="A clear record of every action." description="System events and operational changes, newest first.">
      <AdminSearch placeholder="Search events or people…"/>
      <Button variant="outline" onClick={load}>Refresh</Button>
      <Button variant="outline" onClick={exportCsv} disabled={!rows.length}><Icon name="download" size={17}/> Export logs</Button>
    </AdminPageHeader>
    {error && <ErrorBanner message={error} onRetry={load}/>}
    <section className="admin-card">
      {logs === null && !error ? <Loading/> : <>
        <DataTable headers={["Time", "Event", "Actor", "Details"]} empty={rows.length === 0 && <EmptyState title="No events found"/>}>
          {paged.slice.map((l) => {
            const t = logTime(l.timestamp);
            return <tr key={l.logId}>
              <td><span className="plate">{t.time}</span><small className="subcell">{t.date}</small></td>
              <td><strong>{l.event}</strong></td>
              <td>{l.actor}</td>
              <td className="muted wrap">{l.details}</td>
            </tr>;
          })}
        </DataTable>
        <TableFooter shown={paged.slice.length} total={rows.length} noun="events" {...paged}/>
      </>}
    </section>
  </>;
}

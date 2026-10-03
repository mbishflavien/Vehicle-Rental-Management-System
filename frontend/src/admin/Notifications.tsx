import { useCallback, useEffect, useMemo, useState } from "react";
import { api, errorMessage, type Notification } from "../api";
import { Button, DataTable, EmptyState, ErrorBanner, Icon, Loading, StatusPill, TableFooter, useEscape, usePaged } from "../components/ui";
import { relativeTime } from "../format";
import { AdminPageHeader, AdminSearch, matches, useAdmin } from "./AdminShell";

export function NotificationPreview({ n, close }: { n: Notification; close: () => void }) {
  useEscape(useCallback(close, [close]));
  return <div className="modal-overlay" onMouseDown={close}>
    <div className="modal-card preview-card" role="dialog" aria-modal="true" aria-labelledby="preview-title" onMouseDown={(e) => e.stopPropagation()}>
      <div className="modal-head"><div><span className="eyebrow">{n.channel === "EMAIL" ? "Email" : "Text message"} · {n.eventType}</span><h2 id="preview-title">{n.subject ?? "SMS"}</h2><p>To {n.recipient} · {relativeTime(n.createdAt)} · via {n.provider}</p></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="modal-body">
        {n.channel === "EMAIL"
          // Rendered in a sandboxed frame: no scripts, no access to this page
          ? <iframe className="email-frame" title="Email preview" sandbox="" srcDoc={n.body}/>
          : <div className="sms-bubble">{n.body}</div>}
        {n.error && <p className="form-error">Delivery failed: {n.error}</p>}
      </div>
    </div>
  </div>;
}

export default function Notifications() {
  const { query } = useAdmin();
  const [items, setItems] = useState<Notification[] | null>(null);
  const [error, setError] = useState("");
  const [channel, setChannel] = useState("");
  const [open, setOpen] = useState<Notification | null>(null);

  const load = () => {
    setError("");
    api.notifications().then(setItems).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  const rows = useMemo(() => (items ?? []).filter((n) => (!channel || n.channel === channel) &&
    matches(query, n.recipient, n.subject, n.body.slice(0, 200), n.eventType)), [items, channel, query]);
  const paged = usePaged(rows, 15);
  const failed = (items ?? []).filter((n) => n.status === "FAILED").length;

  return <>
    <AdminPageHeader title="Every message, delivered." description="Emails and texts sent by the RabbitMQ consumers, newest first.">
      <AdminSearch placeholder="Search recipient or subject…"/>
      <label className="select-button"><select value={channel} onChange={(e) => setChannel(e.target.value)} aria-label="Channel">
        <option value="">Email &amp; SMS</option><option value="EMAIL">Email only</option><option value="SMS">SMS only</option>
      </select><Icon name="chevron" size={16}/></label>
      <Button variant="outline" onClick={load}>Refresh</Button>
    </AdminPageHeader>
    {failed > 0 && <ErrorBanner message={`${failed} message${failed === 1 ? "" : "s"} failed to deliver. RabbitMQ retried them before moving them to the dead-letter queue.`}/>}
    {error && <ErrorBanner message={error} onRetry={load}/>}
    <section className="admin-card">
      {items === null && !error ? <Loading/> : <>
        <DataTable headers={["Time", "Channel", "Recipient", "Message", "Event", "Status"]}
          empty={rows.length === 0 && <EmptyState title="No messages yet" text="Bookings, approvals and returns send emails and texts automatically."/>}>
          {paged.slice.map((n) => <tr key={n.notificationId} className="clickable" onClick={() => setOpen(n)}>
            <td className="muted">{relativeTime(n.createdAt)}</td>
            <td><span className={`channel channel-${n.channel.toLowerCase()}`}><Icon name={n.channel === "EMAIL" ? "mail" : "phone"} size={14}/>{n.channel === "EMAIL" ? "Email" : "SMS"}</span></td>
            <td>{n.recipient}</td>
            <td className="wrap"><strong>{n.subject ?? n.body}</strong></td>
            <td><span className="plate">{n.eventType}</span></td>
            <td><StatusPill status={n.status}/></td>
          </tr>)}
        </DataTable>
        <TableFooter shown={paged.slice.length} total={rows.length} noun="messages" {...paged}/>
      </>}
    </section>
    {open && <NotificationPreview n={open} close={() => setOpen(null)}/>}
  </>;
}

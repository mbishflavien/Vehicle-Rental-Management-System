import { useCallback } from "react";
import type { Notification } from "../api";
import { relativeTime } from "../format";
import { Icon, useEscape } from "./ui";

/** Shows one sent email (rendered in a sandboxed frame) or text message. */
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

import { useCallback, useEffect, useState } from "react";
import { api, errorMessage, fetchFileUrl, type Customer, type CustomerDocument, type DocumentType } from "../api";
import { documentTypeLabel, fileSize, relativeTime } from "../format";
import { Button, EmptyState, Icon, Loading, Select, StatusPill, useEscape, useFeedback } from "./ui";

const TYPES: DocumentType[] = ["DRIVER_LICENSE", "NATIONAL_ID", "PASSPORT", "OTHER"];

async function openFile(path: string, toast: (t: string, k?: "success" | "error") => void) {
  const tab = window.open("", "_blank");
  try {
    const url = await fetchFileUrl(path);
    if (tab) tab.location.href = url; else window.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    tab?.close();
    toast(errorMessage(e), "error");
  }
}

function DocumentItem({ doc, children }: { doc: CustomerDocument; children?: React.ReactNode }) {
  return <li className="doc-item">
    <div><strong>{documentTypeLabel(doc.type)}</strong><small>{doc.fileName} · {fileSize(doc.sizeBytes)} · {relativeTime(doc.uploadedAt)}</small></div>
    <StatusPill status={doc.status}/>
    {doc.reviewNote && <div className="doc-note">Note from {doc.reviewedBy}: {doc.reviewNote}</div>}
    <div className="doc-actions">{children}</div>
  </li>;
}

/** Customer's own documents on the profile page. */
export function DocumentsPanel() {
  const { toast, confirm } = useFeedback();
  const [docs, setDocs] = useState<CustomerDocument[] | null>(null);
  const [type, setType] = useState<DocumentType>("DRIVER_LICENSE");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = () => api.myDocuments().then(setDocs).catch((e) => setError(errorMessage(e)));
  useEffect(() => { load(); }, []);

  const upload = async () => {
    if (!file) { setError("Choose a file to upload"); return; }
    if (file.size > 5 * 1024 * 1024) { setError("Files must be 5 MB or smaller"); return; }
    setBusy(true);
    setError("");
    try {
      await api.uploadMyDocument(type, file);
      toast(`${documentTypeLabel(type)} uploaded. We'll review it before your pickup.`);
      setFile(null);
      load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (d: CustomerDocument) => {
    if (!await confirm({ title: "Remove this document?", message: <>{d.fileName} will be deleted.</>, confirmLabel: "Remove", danger: true })) return;
    try { await api.deleteMyDocument(d.documentId); load(); } catch (e) { toast(errorMessage(e), "error"); }
  };

  return <section className="docs-panel">
    <h2>Your documents</h2>
    <p>Upload a photo or scan of your driver license so we can verify it before pickup. PDF, JPEG or PNG, up to 5 MB.</p>
    <Select label="Document type" value={type} onChange={(t) => setType(t as DocumentType)} options={TYPES.map((t) => ({ value: t, label: documentTypeLabel(t) }))}/>
    <div style={{ height: 12 }}/>
    <label className="file-pick">
      <Icon name="upload" size={18}/>{file ? `${file.name} · ${fileSize(file.size)}` : "Choose a file…"}
      <input type="file" accept="application/pdf,image/jpeg,image/png" onChange={(e) => { setFile(e.target.files?.[0] ?? null); setError(""); }}/>
    </label>
    {error && <p className="form-error">{error}</p>}
    <Button className="full" variant="outline" busy={busy} onClick={upload} disabled={!file}>Upload document</Button>
    {docs === null ? <Loading/> : docs.length > 0 && <ul className="doc-list">
      {docs.map((d) => <DocumentItem key={d.documentId} doc={d}>
        <button onClick={() => openFile(`/me/documents/${d.documentId}/content`, toast)}>View</button>
        {d.status !== "VERIFIED" && <button className="danger" onClick={() => remove(d)}>Remove</button>}
      </DocumentItem>)}
    </ul>}
  </section>;
}

/** Staff: review a customer's documents. */
export function CustomerDocumentsDrawer({ customer, canReview, close }: { customer: Customer; canReview: boolean; close: () => void }) {
  const { toast } = useFeedback();
  const [docs, setDocs] = useState<CustomerDocument[] | null>(null);
  const [error, setError] = useState("");
  useEscape(useCallback(close, [close]));

  const load = useCallback(() => {
    api.customerDocuments(customer.customerId).then(setDocs).catch((e) => setError(errorMessage(e)));
  }, [customer.customerId]);
  useEffect(load, [load]);

  const review = async (d: CustomerDocument, status: "VERIFIED" | "REJECTED") => {
    const note = status === "REJECTED" ? window.prompt("Reason shown to the customer (optional)") ?? undefined : undefined;
    try {
      await api.reviewDocument(d.documentId, status, note);
      toast(`${documentTypeLabel(d.type)} ${status === "VERIFIED" ? "verified" : "rejected"}`);
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  return <div className="drawer-overlay" onMouseDown={close}>
    <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="docs-title" onMouseDown={(e) => e.stopPropagation()}>
      <div className="drawer-head"><div><span className="eyebrow">Customer documents</span><h2 id="docs-title">{customer.fullName}</h2><p>{customer.driverLicenseNumber} · stored in MongoDB GridFS</p></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="drawer-body">
        {error && <p className="form-error">{error}</p>}
        {docs === null && !error ? <Loading/> : docs?.length === 0
          ? <EmptyState title="No documents yet" text="The customer can upload them from their profile page."/>
          : <ul className="doc-list">{docs?.map((d) => <DocumentItem key={d.documentId} doc={d}>
              <button onClick={() => openFile(`/documents/${d.documentId}/content`, toast)}>View</button>
              {canReview && d.status !== "VERIFIED" && <button onClick={() => review(d, "VERIFIED")}>Mark verified</button>}
              {canReview && d.status !== "REJECTED" && <button className="danger" onClick={() => review(d, "REJECTED")}>Reject</button>}
            </DocumentItem>)}</ul>}
      </div>
      <div className="drawer-foot"><Button variant="outline" onClick={close}>Close</Button></div>
    </aside>
  </div>;
}

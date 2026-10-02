import { createContext, useCallback, useContext, useEffect, useRef, useState, type InputHTMLAttributes, type ReactNode } from "react";
import { Link } from "react-router-dom";

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    arrow: <><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></>,
    search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
    calendar: <><path d="M6 3v3M18 3v3M4 9h16"/><rect x="3" y="5" width="18" height="16" rx="3"/></>,
    location: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></>,
    chevron: <path d="m8 10 4 4 4-4"/>,
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
    car: <><path d="m5 11 2-5h10l2 5"/><path d="M4 11h16v7H4z"/><circle cx="7" cy="18" r="1.5"/><circle cx="17" cy="18" r="1.5"/></>,
    users: <><circle cx="9" cy="8" r="4"/><path d="M3 21v-2a6 6 0 0 1 12 0v2M16 4a4 4 0 0 1 0 8M18 15a6 6 0 0 1 3 5"/></>,
    file: <><path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5M9 12h6M9 16h6"/></>,
    logs: <><path d="M4 5h16M4 12h16M4 19h16"/><circle cx="7" cy="5" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="10" cy="19" r="1"/></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></>,
    more: <><circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/></>,
    trend: <><path d="m4 16 6-6 4 4 6-7"/><path d="M15 7h5v5"/></>,
    plus: <path d="M12 5v14M5 12h14"/>,
    close: <path d="m6 6 12 12M18 6 6 18"/>,
    download: <><path d="M12 3v12m0 0 5-5m-5 5-5-5"/><path d="M5 21h14"/></>,
    menu: <><path d="M4 7h16M4 12h16M4 17h16"/></>,
    logout: <><path d="M15 4h4v16h-4"/><path d="M10 8 6 12l4 4M6 12h10"/></>,
    check: <path d="m5 12 5 5 9-10"/>,
  };
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

type ButtonProps = {
  children: ReactNode;
  variant?: "primary" | "outline" | "ghost" | "danger";
  className?: string;
  onClick?: () => void;
  type?: "button" | "submit";
  disabled?: boolean;
  busy?: boolean;
};

export function Button({ children, variant = "primary", className = "", onClick, type = "button", disabled, busy }: ButtonProps) {
  return <button type={type} className={`button button-${variant} ${className}`} onClick={onClick} disabled={disabled || busy} aria-busy={busy || undefined}>
    {busy ? <span className="spinner" aria-hidden="true"/> : null}{children}
  </button>;
}

export function ButtonLink({ to, children, variant = "primary", className = "" }: { to: string; children: ReactNode; variant?: "primary" | "outline" | "ghost"; className?: string }) {
  return <Link to={to} className={`button button-${variant} ${className}`}>{children}</Link>;
}

type FieldProps = {
  label: string;
  icon?: string;
  error?: string;
  hint?: string;
} & InputHTMLAttributes<HTMLInputElement>;

/** Labelled input in the design's style, with an inline error under it. */
export function Field({ label, icon, error, hint, className = "", ...input }: FieldProps) {
  return <label className={`field ${error ? "has-error" : ""} ${className}`}>
    <span>{label}</span>
    <div className="field-control">{icon && <Icon name={icon}/>}<input {...input} aria-invalid={!!error || undefined}/></div>
    {error ? <small className="field-error">{error}</small> : hint ? <small className="field-hint">{hint}</small> : null}
  </label>;
}

type SelectProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string; disabled?: boolean }[];
  icon?: string;
  error?: string;
  hint?: string;
  className?: string;
  placeholder?: string;
};

export function Select({ label, value, onChange, options, icon, error, hint, className = "", placeholder }: SelectProps) {
  return <label className={`field ${error ? "has-error" : ""} ${className}`}>
    <span>{label}</span>
    <div className="field-control select-control">
      {icon && <Icon name={icon}/>}
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-invalid={!!error || undefined}>
        {placeholder !== undefined && <option value="" disabled>{placeholder}</option>}
        {options.map((o) => <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>)}
      </select>
      <Icon name="chevron" size={16}/>
    </div>
    {error ? <small className="field-error">{error}</small> : hint ? <small className="field-hint">{hint}</small> : null}
  </label>;
}

export function StatusPill({ status }: { status: string }) {
  const text = status.charAt(0) + status.slice(1).toLowerCase();
  return <span className={`status status-${status.toLowerCase()}`}><i/>{text}</span>;
}

export function Wordmark({ admin = false, to = "/" }: { admin?: boolean; to?: string }) {
  return <Link to={to} className="wordmark" aria-label="VRMS home"><span className="mark">V</span><span>VRMS <em>{admin ? "Admin" : "Mobility"}</em></span></Link>;
}

export function DataTable({ headers, children, empty }: { headers: string[]; children: ReactNode; empty?: ReactNode }) {
  return <div className="table-wrap"><table><thead><tr>{headers.map((h, i) => <th key={h || i}>{h}</th>)}</tr></thead><tbody>{children}</tbody></table>{empty}</div>;
}

export function EmptyState({ title, text, children }: { title: string; text?: string; children?: ReactNode }) {
  return <div className="empty-state"><strong>{title}</strong>{text && <p>{text}</p>}{children}</div>;
}

export function Loading({ label = "Loading…" }: { label?: string }) {
  return <div className="loading" role="status"><span className="spinner"/>{label}</div>;
}

export function ErrorBanner({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <div className="error-banner" role="alert"><span>{message}</span>{onRetry && <button onClick={onRetry}>Try again</button>}</div>;
}

// --- Row action menu ("⋮" button) -------------------------------------------------------------------

export interface MenuAction { label: string; onSelect: () => void; danger?: boolean; disabled?: boolean }

export function RowMenu({ actions }: { actions: MenuAction[] }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", esc); };
  }, [open]);
  return <div className="row-menu" ref={ref}>
    <button className="more-button" aria-label="More actions" aria-expanded={open} onClick={() => setOpen(!open)}><Icon name="more"/></button>
    {open && <div className="row-menu-list" role="menu">
      {actions.map((a) => <button key={a.label} role="menuitem" className={a.danger ? "danger" : ""} disabled={a.disabled}
        onClick={() => { setOpen(false); a.onSelect(); }}>{a.label}</button>)}
    </div>}
  </div>;
}

// --- Pagination -----------------------------------------------------------------------------------

export function usePaged<T>(rows: T[], pageSize = 10) {
  const [page, setPage] = useState(1);
  const pages = Math.max(1, Math.ceil(rows.length / pageSize));
  const current = Math.min(page, pages);
  return { page: current, pages, setPage, slice: rows.slice((current - 1) * pageSize, current * pageSize) };
}

export function TableFooter({ shown, total, noun, page, pages, setPage }: { shown: number; total: number; noun: string; page: number; pages: number; setPage: (p: number) => void }) {
  return <div className="table-footer">
    <span>Showing {shown} of {total} {noun}</span>
    {pages > 1 && <div>
      <button onClick={() => setPage(page - 1)} disabled={page === 1} aria-label="Previous page">‹</button>
      {Array.from({ length: pages }, (_, i) => i + 1).map((p) => <button key={p} className={p === page ? "active" : ""} onClick={() => setPage(p)}>{p}</button>)}
      <button onClick={() => setPage(page + 1)} disabled={page === pages} aria-label="Next page">›</button>
    </div>}
  </div>;
}

// --- Toasts & confirmation dialog ---------------------------------------------------------------------

interface Toast { id: number; text: string; kind: "success" | "error" }
interface ConfirmRequest { title: string; message: ReactNode; confirmLabel: string; danger?: boolean; resolve: (ok: boolean) => void }

interface FeedbackApi {
  toast: (text: string, kind?: "success" | "error") => void;
  confirm: (opts: { title: string; message: ReactNode; confirmLabel?: string; danger?: boolean }) => Promise<boolean>;
}

const FeedbackContext = createContext<FeedbackApi | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [pending, setPending] = useState<ConfirmRequest | null>(null);

  const toast = useCallback((text: string, kind: "success" | "error" = "success") => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, text, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500);
  }, []);

  const confirm = useCallback<FeedbackApi["confirm"]>((opts) => new Promise((resolve) => {
    setPending({ confirmLabel: "Confirm", ...opts, resolve });
  }), []);

  const answer = (ok: boolean) => { pending?.resolve(ok); setPending(null); };

  return <FeedbackContext.Provider value={{ toast, confirm }}>
    {children}
    <div className="toasts" aria-live="polite">
      {toasts.map((t) => <div key={t.id} className={`toast toast-${t.kind}`}><Icon name={t.kind === "success" ? "check" : "close"} size={16}/>{t.text}</div>)}
    </div>
    {pending && <div className="modal-overlay" onMouseDown={() => answer(false)}>
      <div className="modal-card confirm-card" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-body">
          <h2 id="confirm-title">{pending.title}</h2>
          <div className="confirm-message">{pending.message}</div>
        </div>
        <div className="modal-foot">
          <Button variant="outline" onClick={() => answer(false)}>Cancel</Button>
          <Button variant={pending.danger ? "danger" : "primary"} onClick={() => answer(true)}>{pending.confirmLabel}</Button>
        </div>
      </div>
    </div>}
  </FeedbackContext.Provider>;
}

export function useFeedback(): FeedbackApi {
  const ctx = useContext(FeedbackContext);
  if (!ctx) throw new Error("useFeedback must be used inside FeedbackProvider");
  return ctx;
}

/** Closes overlays on Escape. */
export function useEscape(onEscape: () => void) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === "Escape") onEscape(); };
    document.addEventListener("keydown", h);
    return () => document.removeEventListener("keydown", h);
  }, [onEscape]);
}

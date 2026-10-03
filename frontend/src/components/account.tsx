import { useCallback, useState, type FormEvent } from "react";
import { api, ApiError } from "../api";
import { Button, Field, Icon, useEscape, useFeedback } from "./ui";

export function ChangePasswordModal({ close }: { close: () => void }) {
  const { toast } = useFeedback();
  const [form, setForm] = useState({ current: "", next: "", confirm: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  useEscape(useCallback(close, [close]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!form.current) local.currentPassword = "Enter your current password";
    if (form.next.length < 8) local.newPassword = "Password must be at least 8 characters";
    else if (!/[A-Za-z]/.test(form.next) || !/\d/.test(form.next)) local.newPassword = "Password must contain letters and numbers";
    if (form.confirm !== form.next) local.confirm = "Passwords don't match";
    setErrors(local);
    if (Object.keys(local).length) return;
    setBusy(true);
    try {
      await api.changePassword(form.current, form.next);
      toast("Password changed");
      close();
    } catch (err) {
      if (err instanceof ApiError) setErrors(Object.keys(err.fieldErrors).length ? err.fieldErrors : { form: err.message });
    } finally {
      setBusy(false);
    }
  };

  return <div className="modal-overlay" onMouseDown={close}>
    <form className="modal-card confirm-card" role="dialog" aria-modal="true" aria-labelledby="pw-title" onMouseDown={(e) => e.stopPropagation()} onSubmit={submit} noValidate>
      <div className="modal-head"><div><span className="eyebrow">Your account</span><h2 id="pw-title">Change password</h2></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="modal-body">
        <Field label="Current password" type="password" autoComplete="current-password" value={form.current} onChange={(e) => setForm({ ...form, current: e.target.value })} error={errors.currentPassword} autoFocus/>
        <Field label="New password" type="password" autoComplete="new-password" value={form.next} onChange={(e) => setForm({ ...form, next: e.target.value })} error={errors.newPassword} hint="At least 8 characters, with letters and numbers."/>
        <Field label="Confirm new password" type="password" autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} error={errors.confirm}/>
        {errors.form && <p className="form-error">{errors.form}</p>}
      </div>
      <div className="modal-foot"><Button variant="outline" onClick={close}>Cancel</Button><Button type="submit" busy={busy}>Update password</Button></div>
    </form>
  </div>;
}

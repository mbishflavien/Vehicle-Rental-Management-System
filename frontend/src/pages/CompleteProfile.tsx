import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { DocumentsPanel } from "../components/documents";
import { PublicNav } from "../components/public";
import { Button, Field, Icon, useFeedback } from "../components/ui";
import { images } from "../format";

const safeNext = (next: string | null) => (next && next.startsWith("/") && !next.startsWith("//") ? next : null);

/** Phone and driver license, required before a customer can book (BR-01, BR-02). */
export default function CompleteProfilePage() {
  const { user, refresh } = useAuth();
  const { toast } = useFeedback();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get("next"));
  const [form, setForm] = useState({ phoneNumber: "", driverLicenseNumber: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!user?.customerId) return;
    api.myProfile().then((c) => setForm({ phoneNumber: c.phoneNumber ?? "", driverLicenseNumber: c.driverLicenseNumber })).catch(() => {});
  }, [user?.customerId]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (form.phoneNumber && !/^\+?[0-9 ]{9,16}$/.test(form.phoneNumber.trim())) local.phoneNumber = "Phone number is not valid, e.g. +250 788 123 456";
    if (!/^DL-[A-Z0-9-]+$/i.test(form.driverLicenseNumber.trim())) local.driverLicenseNumber = "Driver License must start with 'DL-', e.g. DL-48219";
    setErrors(local);
    if (Object.keys(local).length) return;
    setBusy(true);
    try {
      await api.saveMyProfile({ phoneNumber: form.phoneNumber.trim(), driverLicenseNumber: form.driverLicenseNumber.trim().toUpperCase() });
      await refresh();
      toast("Profile saved. You're ready to book.");
      navigate(next ?? "/fleet", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) setErrors(Object.keys(err.fieldErrors).length ? err.fieldErrors : { form: err.message });
    } finally {
      setBusy(false);
    }
  };

  return <div className="auth-page">
    <PublicNav/>
    <div className="auth-layout">
      <div className="auth-image" style={{ backgroundImage: `url(${images.road})` }}><div>{user?.customerId
        ? <><span className="eyebrow light">Your profile</span><h2>Ready when you are.</h2><p>Keep your details current so pickup takes minutes.</p></>
        : <><span className="eyebrow light">Almost there</span><h2>One last detail.</h2><p>We need your driver license before handing over the keys.</p></>}</div></div>
      <div className="auth-panel">
        <form className="auth-card" onSubmit={submit} noValidate>
          <h1>{user?.customerId ? "Your details." : `Welcome, ${user?.fullName.split(" ")[0] ?? ""}.`}</h1>
          <p>{user?.customerId ? "Keep your contact and license details up to date." : "Add your phone and driver license to start booking."}</p>
          <Field label="Phone number" type="tel" placeholder="+250 7XX XXX XXX" autoComplete="tel" value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} error={errors.phoneNumber}/>
          <Field label="Driver license number" placeholder="DL-XXXXX" value={form.driverLicenseNumber} onChange={(e) => setForm({ ...form, driverLicenseNumber: e.target.value.toUpperCase() })} error={errors.driverLicenseNumber} autoFocus/>
          {errors.form && <p className="form-error" role="alert">{errors.form}</p>}
          <Button type="submit" className="full" busy={busy}>Save and continue <Icon name="arrow" size={17}/></Button>
        </form>
        {user?.customerId && <DocumentsPanel/>}
      </div>
    </div>
  </div>;
}

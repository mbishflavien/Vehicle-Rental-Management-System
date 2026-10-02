import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { PublicNav } from "../components/public";
import { Button, Field, Icon, Wordmark } from "../components/ui";
import { images } from "../format";

type Tab = "signin" | "register";

/** Only follow same-site paths after sign-in. */
const safeNext = (next: string | null) => (next && next.startsWith("/") && !next.startsWith("//") ? next : null);

export default function AuthPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get("tab") === "register" ? "register" : "signin";
  const next = safeNext(params.get("next"));
  const { user, signIn } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ fullName: "", email: "", phoneNumber: "", driverLicenseNumber: "", password: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [showForgot, setShowForgot] = useState(false);
  const [busy, setBusy] = useState(false);

  if (user && !busy) return <Navigate to={next ?? (user.role === "ADMIN" ? "/admin" : "/account/bookings")} replace/>;

  const setTab = (t: Tab) => {
    const p = new URLSearchParams(params);
    if (t === "register") p.set("tab", "register"); else p.delete("tab");
    setParams(p, { replace: true });
    setErrors({});
    setFormError("");
  };

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value });

  const validate = () => {
    const e: Record<string, string> = {};
    if (tab === "register") {
      if (!form.fullName.trim()) e.fullName = "Full name is required";
      if (form.phoneNumber && !/^\+?[0-9 ]{9,16}$/.test(form.phoneNumber.trim())) e.phoneNumber = "Phone number is not valid, e.g. +250 788 123 456";
      if (!/^DL-[A-Z0-9-]+$/i.test(form.driverLicenseNumber.trim())) e.driverLicenseNumber = "Driver License must start with 'DL-', e.g. DL-48219";
      if (form.password.length < 8) e.password = "Password must be at least 8 characters";
    } else if (!form.password) {
      e.password = "Password is required";
    }
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) e.email = "Enter a valid email address";
    return e;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local = validate();
    setErrors(local);
    setFormError("");
    if (Object.keys(local).length) return;
    setBusy(true);
    try {
      const res = tab === "signin"
        ? await api.login(form.email.trim(), form.password)
        : await api.register({ ...form, fullName: form.fullName.trim(), email: form.email.trim(), phoneNumber: form.phoneNumber.trim(), driverLicenseNumber: form.driverLicenseNumber.trim().toUpperCase() });
      signIn(res);
      navigate(next ?? (res.user.role === "ADMIN" ? "/admin" : "/fleet"), { replace: true });
    } catch (err) {
      if (err instanceof ApiError) { setErrors(err.fieldErrors); setFormError(err.message); }
    } finally {
      setBusy(false);
    }
  };

  const registering = tab === "register";

  return <div className="auth-page">
    <PublicNav/>
    <div className="auth-layout">
      <div className="auth-image" style={{ backgroundImage: `url(${images.scenic})` }}><div><span className="eyebrow light">Your next road</span><h2>Rwanda is waiting.</h2><p>Sign in to manage your bookings and get back on the road.</p></div></div>
      <div className="auth-panel">
        <Wordmark/>
        <form className="auth-card" onSubmit={submit} noValidate>
          <div className="auth-tabs" role="tablist">
            <button type="button" role="tab" aria-selected={!registering} className={!registering ? "active" : ""} onClick={() => setTab("signin")}>Sign in</button>
            <button type="button" role="tab" aria-selected={registering} className={registering ? "active" : ""} onClick={() => setTab("register")}>Create account</button>
          </div>
          <h1>{registering ? "Begin your journey." : "Welcome back."}</h1>
          <p>{registering ? "Create an account and reserve with confidence." : "Enter your details to access your VRMS portal."}</p>
          {next && !registering && <div className="notice compact"><span>i</span><p>Sign in or create an account to finish your booking.</p></div>}
          {registering && <Field label="Full name" placeholder="Your full name" autoComplete="name" value={form.fullName} onChange={set("fullName")} error={errors.fullName}/>}
          <Field label="Email address" type="email" placeholder="you@email.com" autoComplete="email" value={form.email} onChange={set("email")} error={errors.email}/>
          {registering && <>
            <Field label="Phone number" type="tel" placeholder="+250 7XX XXX XXX" autoComplete="tel" value={form.phoneNumber} onChange={set("phoneNumber")} error={errors.phoneNumber}/>
            <Field label="Driver license number" placeholder="DL-XXXXX" value={form.driverLicenseNumber} onChange={set("driverLicenseNumber")} error={errors.driverLicenseNumber}/>
          </>}
          <Field label="Password" type="password" placeholder="••••••••" autoComplete={registering ? "new-password" : "current-password"} value={form.password} onChange={set("password")} error={errors.password} hint={registering ? "At least 8 characters." : undefined}/>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <Button type="submit" className="full" busy={busy}>{registering ? "Register & continue" : "Sign in to portal"} <Icon name="arrow" size={17}/></Button>
          {!registering && <button type="button" className="forgot" onClick={() => setShowForgot(!showForgot)}>Forgot your password?</button>}
          {showForgot && !registering && <p className="field-hint center">Contact the VRMS team at <a href="mailto:hello@vrms.rw">hello@vrms.rw</a> or +250 788 220 440 and we'll reset it for you.</p>}
          <div className="staff-entry"><span>VRMS team member?</span><Link to="/admin">Open staff console</Link></div>
        </form>
      </div>
    </div>
  </div>;
}

import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api";
import { isStaff, useAuth } from "../auth";
import { PublicNav } from "../components/public";
import { Button, Field, Icon, Wordmark } from "../components/ui";
import { images } from "../format";
import { OAUTH_NEXT_KEY } from "./OAuthCallback";

type Tab = "signin" | "register";

const GoogleLogo = () => <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"/></svg>;
const GitHubLogo = () => <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path d="M12 .5C5.7.5.5 5.7.5 12c0 5.1 3.3 9.4 7.9 10.9.6.1.8-.3.8-.6v-2c-3.2.7-3.9-1.5-3.9-1.5-.5-1.3-1.3-1.7-1.3-1.7-1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1 1.8 2.8 1.3 3.5 1 .1-.8.4-1.3.7-1.6-2.6-.3-5.3-1.3-5.3-5.7 0-1.3.5-2.3 1.2-3.1-.1-.3-.5-1.5.1-3.1 0 0 1-.3 3.2 1.2a11 11 0 0 1 5.8 0c2.2-1.5 3.2-1.2 3.2-1.2.6 1.6.2 2.8.1 3.1.8.8 1.2 1.9 1.2 3.1 0 4.4-2.7 5.4-5.3 5.7.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A11.5 11.5 0 0 0 23.5 12C23.5 5.7 18.3.5 12 .5z"/></svg>;

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
  const [providers, setProviders] = useState<string[]>([]);
  const oauthError = params.get("oauthError");

  useEffect(() => { api.providers().then(setProviders).catch(() => {}); }, []);

  const startOAuth = (provider: string) => {
    try { if (next) sessionStorage.setItem(OAUTH_NEXT_KEY, next); } catch { /* ignore */ }
    window.location.href = `/oauth2/authorization/${provider}`;
  };

  if (user && !busy) return <Navigate to={next ?? (isStaff(user) ? "/admin" : "/account/bookings")} replace/>;

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
      else if (!/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) e.password = "Password must contain letters and numbers";
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
      navigate(next ?? (isStaff(res.user) ? "/admin" : "/fleet"), { replace: true });
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
          <Field label="Password" type="password" placeholder="••••••••" autoComplete={registering ? "new-password" : "current-password"} value={form.password} onChange={set("password")} error={errors.password} hint={registering ? "At least 8 characters, with letters and numbers." : undefined}/>
          {oauthError && !formError && <p className="form-error" role="alert">{oauthError}</p>}
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <Button type="submit" className="full" busy={busy}>{registering ? "Register & continue" : "Sign in to portal"} <Icon name="arrow" size={17}/></Button>
          {providers.length > 0 && <>
            <div className="divider"><span>or</span></div>
            <div className="oauth-buttons">
              {providers.includes("google") && <button type="button" className="oauth-button" onClick={() => startOAuth("google")}><GoogleLogo/> Continue with Google</button>}
              {providers.includes("github") && <button type="button" className="oauth-button" onClick={() => startOAuth("github")}><GitHubLogo/> Continue with GitHub</button>}
            </div>
          </>}
          {!registering && <button type="button" className="forgot" onClick={() => setShowForgot(!showForgot)}>Forgot your password?</button>}
          {showForgot && !registering && <p className="field-hint center">Contact the VRMS team at <a href="mailto:hello@vrms.rw">hello@vrms.rw</a> or +250 788 220 440 and we'll reset it for you.</p>}
          <div className="staff-entry"><span>VRMS team member?</span><Link to="/admin">Open staff console</Link></div>
        </form>
      </div>
    </div>
  </div>;
}

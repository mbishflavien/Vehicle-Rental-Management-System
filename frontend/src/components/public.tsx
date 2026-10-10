import { useCallback, useMemo, useState, type FormEvent } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { api, ApiError, type Vehicle } from "../api";
import { isStaff, useAuth } from "../auth";
import { branchOptions, useBranches } from "../branches";
import { addDays, daysBetween, label, plate, rwf, specs, today, vehicleImage } from "../format";
import { Button, ButtonLink, Field, Icon, Select, StatusPill, useEscape, useFeedback, Wordmark } from "./ui";

export function PublicNav({ className = "" }: { className?: string }) {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);

  const links = <>
    <NavLink to="/fleet" onClick={close}>Browse Fleet</NavLink>
    <Link to="/#solutions" onClick={close}>Solutions</Link>
    {isStaff(user)
      ? <NavLink to="/admin" onClick={close}>Staff console</NavLink>
      : <Link to="/#corporate" onClick={close}>Corporate</Link>}
    {user?.role === "CUSTOMER" && <NavLink to="/account/bookings" onClick={close}>My bookings</NavLink>}
    {user?.role === "CUSTOMER" && <NavLink to="/account/profile" onClick={close}>Profile</NavLink>}
    {user
      ? <button onClick={() => { close(); signOut(); navigate("/"); }}>Sign out</button>
      : <NavLink to="/signin" onClick={close}>Sign In</NavLink>}
    {user
      ? <span className="nav-user" title={user.email}>{user.fullName.split(" ")[0]}</span>
      : <ButtonLink to="/signin?tab=register">Register <Icon name="arrow" size={16}/></ButtonLink>}
  </>;

  return <nav className={`public-nav ${className}`.trim()}>
    <Wordmark/>
    <div className="nav-links">{links}</div>
    <button className="mobile-menu" aria-label={open ? "Close menu" : "Open menu"} aria-expanded={open} onClick={() => setOpen(!open)}>
      <Icon name={open ? "close" : "menu"}/>
    </button>
    {open && <div className="mobile-panel">{links}</div>}
  </nav>;
}

export function Footer() {
  return <footer>
    <div className="footer-main container">
      <div className="footer-brand"><Wordmark/><p>Considered mobility for every road in Rwanda.</p><span>KN 5 Rd, Kigali, Rwanda</span></div>
      <div><h4>Explore</h4><Link to="/fleet">Browse fleet</Link><Link to="/#solutions">How it works</Link><Link to="/#corporate">Corporate rentals</Link><Link to="/signin">Sign in</Link></div>
      <div><h4>Contact</h4><a href="tel:+250788220440">+250 788 220 440</a><a href="mailto:hello@vrms.rw">hello@vrms.rw</a><span>24/7 roadside care</span></div>
      <div><h4>Follow along</h4><a href="https://www.instagram.com" target="_blank" rel="noreferrer">Instagram</a><a href="https://www.linkedin.com" target="_blank" rel="noreferrer">LinkedIn</a><a href="https://www.facebook.com" target="_blank" rel="noreferrer">Facebook</a></div>
    </div>
    <div className="footer-bottom container"><span>© {new Date().getFullYear()} VRMS Mobility. All rights reserved.</span><span><a href="/vehicles/credits.html">Photo credits</a> · Privacy · Terms · Cookies</span></div>
  </footer>;
}

export function VehicleCard({ vehicle, onReserve }: { vehicle: Vehicle; onReserve: (v: Vehicle) => void }) {
  const available = vehicle.vehicleStatus === "AVAILABLE";
  return <article className="vehicle-card">
    <div className="vehicle-image"><img src={vehicleImage(vehicle)} alt={`${vehicle.model}`} loading="lazy"/>{vehicle.category && <span className="image-tag">{label(vehicle.category)}</span>}</div>
    <div className="vehicle-body">
      <div className="vehicle-title"><h3>{vehicle.model}</h3><span className="plate">{plate(vehicle.plateNumber)}</span></div>
      <p>{specs(vehicle)}{vehicle.branch && <> · <span className="branch-tag"><Icon name="location" size={13}/>{vehicle.branch.name}</span></>}</p>
      <div className="vehicle-meta"><div><strong>{rwf(vehicle.dailyRate)}</strong><small>/ day</small></div><StatusPill status={vehicle.vehicleStatus}/></div>
      <Button className="full" disabled={!available} onClick={() => onReserve(vehicle)}>
        {available ? <>Reserve &amp; book <Icon name="arrow" size={17}/></> : "Currently unavailable"}
      </Button>
    </div>
  </article>;
}

/** pickup is a branch id, or "" for any branch. */
export interface TripPrefs { pickup: string; start: string; end: string }

export function defaultTrip(): TripPrefs {
  const start = addDays(today(), 1);
  return { pickup: "", start, end: addDays(start, 5) };
}

/** "Reserve & book" for customers. Signed-out visitors are sent to sign in and brought back. */
export function BookingModal({ vehicle, trip, close, onBooked }: { vehicle: Vehicle; trip: TripPrefs; close: () => void; onBooked: () => void }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { toast } = useFeedback();
  const branches = useBranches();
  const [form, setForm] = useState({ ...trip, pickup: trip.pickup || vehicle.branch?.branchId || "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [busy, setBusy] = useState(false);
  useEscape(useCallback(close, [close]));

  const days = daysBetween(form.start, form.end);
  const total = useMemo(() => (days > 0 ? days * vehicle.dailyRate : 0), [days, vehicle.dailyRate]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const back = `${location.pathname}?book=${vehicle.vehicleId}&start=${form.start}&end=${form.end}&pickup=${encodeURIComponent(form.pickup)}`;
    if (!user) {
      navigate(`/signin?next=${encodeURIComponent(back)}`);
      return;
    }
    if (!user.customerId) {
      navigate(`/account/profile?next=${encodeURIComponent(back)}`);
      return;
    }
    const local: Record<string, string> = {};
    if (form.start < today()) local.startDate = "Start date can't be in the past";
    if (days <= 0) local.endDate = "Return date must be after the start date";
    setErrors(local);
    if (Object.keys(local).length) return;

    setBusy(true);
    setFormError("");
    try {
      await api.book({ vehicleId: vehicle.vehicleId, startDate: form.start, endDate: form.end, pickupBranchId: form.pickup || undefined });
      toast(`${vehicle.model} reserved. The VRMS team will confirm shortly.`);
      onBooked();
      navigate("/account/bookings");
    } catch (err) {
      if (err instanceof ApiError) { setErrors(err.fieldErrors); setFormError(err.message); }
    } finally {
      setBusy(false);
    }
  };

  return <div className="modal-overlay" onMouseDown={close}>
    <form className="modal-card" role="dialog" aria-modal="true" aria-labelledby="book-title" onMouseDown={(e) => e.stopPropagation()} onSubmit={submit} noValidate>
      <div className="modal-head"><div><span className="eyebrow">Reserve &amp; book</span><h2 id="book-title">{vehicle.model}</h2><p>{plate(vehicle.plateNumber)} · {specs(vehicle)}</p></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="modal-body">
        {isStaff(user)
          ? <div className="notice"><span>i</span><p>You're signed in as staff. Issue rentals from the <Link to="/admin/contracts">staff console</Link> instead.</p></div>
          : <>
            <Select label="Pickup branch" icon="location" value={form.pickup} onChange={(pickup) => setForm({ ...form, pickup })}
              placeholder={branches.length ? undefined : "Loading branches…"} options={branchOptions(branches)}
              hint={vehicle.branch && form.pickup && form.pickup !== vehicle.branch.branchId ? `This vehicle is based at ${vehicle.branch.name}; we'll bring it to you.` : undefined}/>
            <div className="field-row">
              <Field label="Start date" type="date" min={today()} value={form.start} error={errors.startDate}
                onChange={(e) => { const start = e.target.value; setForm({ ...form, start, end: form.end <= start ? addDays(start, 1) : form.end }); }}/>
              <Field label="Return date" type="date" min={addDays(form.start, 1)} value={form.end} error={errors.endDate} onChange={(e) => setForm({ ...form, end: e.target.value })}/>
            </div>
            <div className="calculation"><div><span>Estimated total</span><small>{Math.max(days, 0)} day{days === 1 ? "" : "s"} × {rwf(vehicle.dailyRate)}</small></div><strong>{rwf(total)}</strong></div>
            {formError && !Object.keys(errors).length && <p className="form-error">{formError}</p>}
            {errors.vehicleId && <p className="form-error">{errors.vehicleId}</p>}
            <div className="notice"><span>i</span><p>{user
              ? <>Your booking is held as <strong>Pending</strong> and the vehicle is reserved for you until our team confirms it.</>
              : <>You'll be asked to sign in or create an account before we reserve this vehicle.</>}</p></div>
          </>}
      </div>
      <div className="modal-foot">
        <Button variant="outline" onClick={close}>Cancel</Button>
        {!isStaff(user) && <Button type="submit" busy={busy}>{user ? "Confirm booking" : "Sign in to book"} <Icon name="arrow" size={17}/></Button>}
      </div>
    </form>
  </div>;
}

import { useEffect, useState } from "react";
import { api, errorMessage, type Contract, type Notification } from "../api";
import { useAuth } from "../auth";
import { ChangePasswordModal } from "../components/account";
import { NotificationPreview } from "../components/notifications";
import { Footer, PublicNav } from "../components/public";
import { Button, ButtonLink, EmptyState, ErrorBanner, Icon, Loading, StatusPill, useFeedback } from "../components/ui";
import { daysBetween, period, plate, relativeTime, rwf, shortId, vehicleImage } from "../format";

export default function MyBookingsPage() {
  const { user } = useAuth();
  const { toast, confirm } = useFeedback();
  const [bookings, setBookings] = useState<Contract[] | null>(null);
  const [error, setError] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);
  const [messages, setMessages] = useState<Notification[]>([]);
  const [openMessage, setOpenMessage] = useState<Notification | null>(null);

  const load = () => {
    setError("");
    api.myBookings().then(setBookings).catch((e) => setError(errorMessage(e)));
    api.myNotifications().then(setMessages).catch(() => {});
  };
  useEffect(load, []);

  const cancel = async (b: Contract) => {
    const ok = await confirm({
      title: "Cancel this booking?",
      message: <>Your reservation of the <strong>{b.vehicle.model}</strong> for {period(b.startDate, b.endDate)} will be released.</>,
      confirmLabel: "Cancel booking",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.cancelBooking(b.contractId);
      toast("Booking cancelled");
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  return <div className="inner-page">
    <PublicNav/>
    <header className="page-hero compact-hero"><span className="eyebrow">My bookings</span><h1>Hello, {user?.fullName.split(" ")[0]}.</h1><p>Your reservations and rentals with VRMS, newest first.</p><button className="text-link" onClick={() => setChangingPassword(true)}>Change password</button></header>
    <main className="container bookings-list">
      {error && <ErrorBanner message={error} onRetry={load}/>}
      {bookings === null && !error && <Loading/>}
      {bookings?.length === 0 && <EmptyState title="No bookings yet" text="Find a vehicle you like and reserve it in a few clicks."><ButtonLink to="/fleet">Browse the fleet <Icon name="arrow" size={17}/></ButtonLink></EmptyState>}
      {bookings?.map((b) => <article className="booking-card" key={b.contractId}>
        <div className="booking-image"><img src={vehicleImage(b.vehicle)} alt={b.vehicle.model}/></div>
        <div className="booking-body">
          <div className="vehicle-title"><h3>{b.vehicle.model}</h3><StatusPill status={b.contractStatus}/></div>
          <p className="muted">{shortId("CTR", b.contractId)} · <span className="plate">{plate(b.vehicle.plateNumber)}</span></p>
          <dl>
            <div><dt>Rental period</dt><dd>{period(b.startDate, b.endDate)} · {daysBetween(b.startDate, b.endDate)} days</dd></div>
            <div><dt>Pickup</dt><dd>{b.pickupBranch?.name ?? "To be confirmed"}</dd></div>
            <div><dt>Total</dt><dd><strong>{rwf(b.totalCost)}</strong></dd></div>
          </dl>
          {b.contractStatus === "PENDING" && <div className="booking-actions"><span className="muted">Waiting for confirmation from the VRMS team.</span><Button variant="outline" onClick={() => cancel(b)}>Cancel booking</Button></div>}
        </div>
      </article>)}
      {messages.length > 0 && <section className="messages">
        <h2>Messages from VRMS</h2>
        <ul>{messages.slice(0, 8).map((m) => <li key={m.notificationId}><button onClick={() => setOpenMessage(m)}>
          <Icon name={m.channel === "EMAIL" ? "mail" : "phone"} size={16}/>
          <span><strong>{m.subject ?? m.body}</strong><small>{m.channel === "EMAIL" ? "Email" : "SMS"} to {m.recipient} · {relativeTime(m.createdAt)}</small></span>
        </button></li>)}</ul>
      </section>}
    </main>
    <Footer/>
    {openMessage && <NotificationPreview n={openMessage} close={() => setOpenMessage(null)}/>}
    {changingPassword && <ChangePasswordModal close={() => setChangingPassword(false)}/>}
  </div>;
}

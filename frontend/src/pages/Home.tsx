import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, type Vehicle } from "../api";
import { BookingModal, defaultTrip, Footer, PublicNav, VehicleCard } from "../components/public";
import { Button, ButtonLink, Field, Icon, Select } from "../components/ui";
import { branchOptions, useBranches } from "../branches";
import { addDays, CATEGORIES, images, label, today } from "../format";

const faqs = [
  ["What do I need to book a vehicle?", "A valid driver license, national ID or passport, and a payment method. International visitors may use a recognized international driving permit."],
  ["Is insurance included in the rental?", "Yes. Every rental includes comprehensive cover and 24/7 roadside assistance. Optional excess reduction is available at checkout."],
  ["Can you deliver the vehicle to the airport?", "Absolutely. Choose Kigali International Airport as your pickup location and our host will meet you in arrivals."],
  ["Can I change my pickup or return time?", "Yes, up to 12 hours before pickup, subject to availability. Our team is always available to help with itinerary changes."],
];

function SearchCard() {
  const navigate = useNavigate();
  const [trip, setTrip] = useState(defaultTrip);
  const [type, setType] = useState("");
  const branches = useBranches();
  const search = () => {
    const q = new URLSearchParams({ start: trip.start, end: trip.end });
    if (trip.pickup) q.set("pickup", trip.pickup);
    if (type) q.set("type", type);
    navigate(`/fleet?${q}`);
  };
  return <form className="search-card" onSubmit={(e) => { e.preventDefault(); search(); }}>
    <Select label="Pickup location" icon="location" value={trip.pickup} onChange={(pickup) => setTrip({ ...trip, pickup })} options={[{ value: "", label: "Any branch" }, ...branchOptions(branches)]}/>
    <Field label="Start date" type="date" min={today()} value={trip.start}
      onChange={(e) => { const start = e.target.value; setTrip({ ...trip, start, end: trip.end <= start ? addDays(start, 1) : trip.end }); }}/>
    <Field label="End date" type="date" min={addDays(trip.start, 1)} value={trip.end} onChange={(e) => setTrip({ ...trip, end: e.target.value })}/>
    <Select label="Vehicle type" value={type} onChange={setType} options={[{ value: "", label: "All vehicles" }, ...CATEGORIES.map((c) => ({ value: c, label: label(c) }))]}/>
    <Button type="submit">Find vehicles <Icon name="arrow" size={17}/></Button>
  </form>;
}

export default function HomePage() {
  const [openFaq, setOpenFaq] = useState(0);
  const [featured, setFeatured] = useState<Vehicle[] | null>(null);
  const [booking, setBooking] = useState<Vehicle | null>(null);

  const load = () => api.vehicles()
    .then((all) => setFeatured([...all].sort((a, b) => Number(b.vehicleStatus === "AVAILABLE") - Number(a.vehicleStatus === "AVAILABLE")).slice(0, 3)))
    .catch(() => setFeatured([]));
  useEffect(() => { load(); }, []);

  return <div className="public-site">
    <section className="hero" style={{ backgroundImage: `url(${images.hero})` }}>
      <PublicNav/>
      <div className="hero-overlay"/>
      <div className="hero-content">
        <span className="eyebrow light">Rwanda, ready when you are</span>
        <h1>Enterprise &amp; personal<br/>vehicle rentals in Rwanda.</h1>
        <p>Thoughtful mobility for everyday journeys, business travel, and the road less taken.</p>
      </div>
      <div className="scroll-hint"><span/>Explore the journey</div>
    </section>
    <main>
      <div className="search-wrap"><SearchCard/></div>
      <section className="intro container" id="solutions">
        <div className="intro-copy">
          <span className="eyebrow">Movement, made personal</span>
          <h2>More than a car.<br/>A better way forward.</h2>
          <p>From a sunrise drive through Rwanda's thousand hills to the daily rhythm of your business, we make every journey feel considered. A trusted local team, a carefully kept fleet, and support that travels with you.</p>
          <ButtonLink variant="outline" to="/fleet">Discover our approach <Icon name="arrow" size={17}/></ButtonLink>
          <div className="intro-stats"><div><strong>12+</strong><span>Years on the road</span></div><div><strong>98%</strong><span>Guest satisfaction</span></div></div>
        </div>
        <div className="intro-photo"><img src={images.road} alt="A winding road through Rwanda's green hills"/><div className="photo-note"><span>Always within reach</span><strong>24/7</strong><small>Roadside care</small></div></div>
      </section>
      <section className="fleet-section">
        <div className="container">
          <div className="section-head"><div><span className="eyebrow">Featured fleet</span><h2>Find your way to move.</h2></div><ButtonLink variant="ghost" to="/fleet">Browse all vehicles <Icon name="arrow" size={17}/></ButtonLink></div>
          <div className="fleet-grid">
            {featured === null
              ? [0, 1, 2].map((i) => <div key={i} className="vehicle-card skeleton"/>)
              : featured.map((v) => <VehicleCard key={v.vehicleId} vehicle={v} onReserve={setBooking}/>)}
          </div>
          {featured?.length === 0 && <p className="muted center">Our fleet is being prepared. Please check back soon.</p>}
        </div>
      </section>
      <section className="quote-band">
        <div className="quote-inner">
          <div className="quote-mark">“</div>
          <blockquote>Every detail felt effortless — from the airport handover to the drive across the hills. It felt less like renting a car and more like being looked after by a friend.</blockquote>
          <div className="quote-person"><span>AU</span><div><strong>Aline Uwase</strong><small>Kigali · RAV4 guest</small></div></div>
        </div>
      </section>
      <section className="faq container" id="corporate">
        <div className="faq-intro"><span className="eyebrow">Good to know</span><h2>Questions for<br/>the road ahead.</h2><p>Everything you need before turning the key. Corporate fleets and long-term rentals too. Can't find your answer? Our Kigali team is one call away.</p><a className="button button-outline" href="mailto:hello@vrms.rw">Talk to our team</a></div>
        <div className="accordion">{faqs.map((faq, i) => <div className={`faq-item ${openFaq === i ? "open" : ""}`} key={faq[0]}>
          <button onClick={() => setOpenFaq(openFaq === i ? -1 : i)} aria-expanded={openFaq === i}><span>{String(i + 1).padStart(2, "0")}</span><strong>{faq[0]}</strong><i><Icon name="plus"/></i></button>
          {openFaq === i && <p>{faq[1]}</p>}
        </div>)}</div>
      </section>
    </main>
    <Footer/>
    {booking && <BookingModal vehicle={booking} trip={defaultTrip()} close={() => setBooking(null)} onBooked={load}/>}
  </div>;
}

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, errorMessage, type Vehicle } from "../api";
import { BookingModal, defaultTrip, Footer, PublicNav, VehicleCard, type TripPrefs } from "../components/public";
import { EmptyState, ErrorBanner, Icon, Select } from "../components/ui";
import { CATEGORIES, label, PICKUP_LOCATIONS, plate } from "../format";

type Sort = "availability" | "price-asc" | "price-desc" | "model";

export default function FleetPage() {
  const [params, setParams] = useSearchParams();
  const [vehicles, setVehicles] = useState<Vehicle[] | null>(null);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [type, setType] = useState(params.get("type") ?? "");
  const [sort, setSort] = useState<Sort>("availability");
  const [booking, setBooking] = useState<Vehicle | null>(null);

  const fallback = defaultTrip();
  const [trip, setTrip] = useState<TripPrefs>({
    pickup: params.get("pickup") ?? fallback.pickup,
    start: params.get("start") ?? fallback.start,
    end: params.get("end") ?? fallback.end,
  });

  const load = () => {
    setError("");
    api.vehicles().then(setVehicles).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  // Coming back from sign-in with ?book=<id>: reopen the booking for that vehicle.
  useEffect(() => {
    const id = params.get("book");
    if (!id || !vehicles) return;
    const v = vehicles.find((x) => x.vehicleId === id);
    if (v) setBooking(v);
    params.delete("book");
    setParams(params, { replace: true });
  }, [vehicles, params, setParams]);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase().replace(/\s+/g, "");
    const rows = (vehicles ?? []).filter((v) =>
      (!type || v.category === type) &&
      (!q || `${v.model}${v.plateNumber}`.toLowerCase().replace(/\s+/g, "").includes(q)));
    const byAvailability = (v: Vehicle) => (v.vehicleStatus === "AVAILABLE" ? 0 : 1);
    return rows.sort((a, b) => {
      switch (sort) {
        case "price-asc": return a.dailyRate - b.dailyRate;
        case "price-desc": return b.dailyRate - a.dailyRate;
        case "model": return a.model.localeCompare(b.model);
        default: return byAvailability(a) - byAvailability(b) || a.dailyRate - b.dailyRate;
      }
    });
  }, [vehicles, query, type, sort]);

  return <div className="inner-page">
    <PublicNav className="fleet-nav"/>
    <header className="page-hero"><span className="eyebrow">The VRMS collection</span><h1>Choose the right car<br/>for your road.</h1><p>Reliable, beautifully maintained vehicles with local support wherever your journey takes you.</p></header>
    <div className="filter-bar">
      <label><Icon name="search"/><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search model or plate" aria-label="Search model or plate"/></label>
      <Select label="Pickup" value={trip.pickup} onChange={(pickup) => setTrip({ ...trip, pickup })} options={PICKUP_LOCATIONS.map((l) => ({ value: l, label: l }))}/>
      <Select label="Vehicle type" value={type} onChange={setType} options={[{ value: "", label: "All types" }, ...CATEGORIES.map((c) => ({ value: c, label: label(c) }))]}/>
      <Select label="Sort by" value={sort} onChange={(s) => setSort(s as Sort)} options={[
        { value: "availability", label: "Availability" },
        { value: "price-asc", label: "Price: low to high" },
        { value: "price-desc", label: "Price: high to low" },
        { value: "model", label: "Model A–Z" },
      ]}/>
      <span>{shown.length} vehicle{shown.length === 1 ? "" : "s"}</span>
    </div>
    <main className="container">
      {error && <ErrorBanner message={error} onRetry={load}/>}
      <div className="fleet-page-grid">
        {vehicles === null && !error
          ? [0, 1, 2, 3, 4, 5].map((i) => <div key={i} className="vehicle-card skeleton"/>)
          : shown.map((v) => <VehicleCard key={v.vehicleId} vehicle={v} onReserve={setBooking}/>)}
      </div>
      {vehicles && shown.length === 0 && <EmptyState title="No vehicles match your search" text={query ? `Nothing found for "${query}". Try a model name or a plate like ${plate("RAB123A")}.` : "Try another vehicle type."}/>}
    </main>
    <Footer/>
    {booking && <BookingModal vehicle={booking} trip={trip} close={() => setBooking(null)} onBooked={load}/>}
  </div>;
}

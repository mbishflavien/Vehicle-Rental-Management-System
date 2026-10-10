import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { api, ApiError, type Customer, type CustomerInput, type Vehicle, type VehicleInput } from "../api";
import { Button, Field, Icon, Select, useEscape, useFeedback } from "../components/ui";
import { branchOptions, useBranches } from "../branches";
import { addDays, CATEGORIES, label, plate, rwf, today } from "../format";

function Drawer({ eyebrow, title, description, close, onSubmit, busy, submitLabel, children, formError }: {
  eyebrow: string; title: string; description: string; close: () => void; onSubmit: () => void;
  busy: boolean; submitLabel: string; children: ReactNode; formError?: string;
}) {
  useEscape(useCallback(close, [close]));
  const submit = (e: FormEvent) => { e.preventDefault(); onSubmit(); };
  return <div className="drawer-overlay" onMouseDown={close}>
    <form className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" onMouseDown={(e) => e.stopPropagation()} onSubmit={submit} noValidate>
      <div className="drawer-head"><div><span className="eyebrow">{eyebrow}</span><h2 id="drawer-title">{title}</h2><p>{description}</p></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="drawer-body">{children}{formError && <p className="form-error" role="alert">{formError}</p>}</div>
      <div className="drawer-foot"><Button variant="outline" onClick={close}>Cancel</Button><Button type="submit" busy={busy}>{submitLabel} <Icon name="arrow" size={17}/></Button></div>
    </form>
  </div>;
}

/** Shared submit handling: client checks first, then server field errors land under the same inputs. */
function useForm<T>(initial: T) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [busy, setBusy] = useState(false);

  const run = async (local: Record<string, string>, action: () => Promise<void>) => {
    setErrors(local);
    setFormError("");
    if (Object.keys(local).length) return;
    setBusy(true);
    try {
      await action();
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors(e.fieldErrors);
        if (!Object.keys(e.fieldErrors).length) setFormError(e.message);
      } else {
        setFormError("Something went wrong");
      }
    } finally {
      setBusy(false);
    }
  };
  return { values, setValues, errors, formError, busy, run };
}

const PLATE_RE = /^RA[A-Z][0-9]{3}[A-Z]$/;

export function VehicleDrawer({ vehicle, close, saved }: { vehicle: Vehicle | null; close: () => void; saved: (v: Vehicle) => void }) {
  const { toast } = useFeedback();
  const editing = vehicle !== null;
  const locked = vehicle?.vehicleStatus === "RENTED" || vehicle?.vehicleStatus === "RESERVED";
  const branches = useBranches();
  const f = useForm({
    plateNumber: vehicle ? plate(vehicle.plateNumber) : "",
    model: vehicle?.model ?? "",
    category: vehicle?.category ?? "SUV",
    dailyRate: vehicle ? String(vehicle.dailyRate) : "",
    vehicleStatus: vehicle?.vehicleStatus ?? "AVAILABLE",
    transmission: vehicle?.transmission ?? "AUTOMATIC",
    fuelType: vehicle?.fuelType ?? "PETROL",
    seats: vehicle?.seats ? String(vehicle.seats) : "5",
    imageUrl: vehicle?.imageUrl ?? "",
    branchId: vehicle?.branch?.branchId ?? "",
  });
  const v = f.values;
  const set = (key: keyof typeof v) => (value: string) => f.setValues({ ...v, [key]: value });

  const submit = () => {
    const normalized = v.plateNumber.replace(/\s+/g, "").toUpperCase();
    const rate = Number(v.dailyRate.replace(/[, ]/g, ""));
    const seats = Number(v.seats);
    const local: Record<string, string> = {};
    if (!PLATE_RE.test(normalized)) local.plateNumber = "Plate number must follow Rwandan format e.g. RAB 123 A";
    if (!v.model.trim()) local.model = "Model is required";
    if (!(rate > 0)) local.dailyRate = "Daily rate must be greater than zero";
    if (!Number.isInteger(seats) || seats < 1 || seats > 60) local.seats = "Seats must be between 1 and 60";
    if (v.imageUrl && !/^(https?:\/\/.+|\/[A-Za-z0-9._/-]+)$/.test(v.imageUrl.trim())) local.imageUrl = "Image URL must start with http://, https:// or /";

    const body: VehicleInput = {
      plateNumber: normalized, model: v.model.trim(), dailyRate: rate, category: v.category as VehicleInput["category"],
      vehicleStatus: v.vehicleStatus as VehicleInput["vehicleStatus"], transmission: v.transmission as VehicleInput["transmission"],
      fuelType: v.fuelType as VehicleInput["fuelType"], seats, imageUrl: v.imageUrl.trim() || null,
      branchId: v.branchId || null,
    };
    f.run(local, async () => {
      const result = editing ? await api.updateVehicle(vehicle.vehicleId, body) : await api.createVehicle(body);
      toast(editing ? `${result.model} updated` : `${result.model} (${plate(result.plateNumber)}) added to the fleet`);
      saved(result);
    });
  };

  const statusOptions = locked
    ? [{ value: v.vehicleStatus, label: `${label(v.vehicleStatus)} (managed by contract)` }]
    : [{ value: "AVAILABLE", label: "Available" }, { value: "MAINTENANCE", label: "Maintenance" }];

  return <Drawer eyebrow="Fleet assets" title={editing ? "Edit vehicle" : "Add new vehicle"} description={editing ? `Update ${vehicle.model}'s details.` : "Enter the vehicle details below."}
    close={close} onSubmit={submit} busy={f.busy} submitLabel="Save vehicle" formError={f.formError}>
    <Field label="Plate number" placeholder="RAB 123 A" value={v.plateNumber} onChange={(e) => set("plateNumber")(e.target.value.toUpperCase())} error={f.errors.plateNumber} hint="Rwandan format: RAA 000 A" autoFocus/>
    <Field label="Vehicle model" placeholder="e.g. Toyota RAV4" value={v.model} onChange={(e) => set("model")(e.target.value)} error={f.errors.model}/>
    <div className="field-row">
      <Select label="Category" value={v.category} onChange={set("category")} options={CATEGORIES.map((c) => ({ value: c, label: label(c) }))} error={f.errors.category}/>
      <Field label="Daily rate (RWF)" placeholder="85,000" inputMode="numeric" value={v.dailyRate} onChange={(e) => set("dailyRate")(e.target.value)} error={f.errors.dailyRate}/>
    </div>
    <div className="field-row">
      <Select label="Transmission" value={v.transmission} onChange={set("transmission")} options={[{ value: "AUTOMATIC", label: "Automatic" }, { value: "MANUAL", label: "Manual" }]}/>
      <Select label="Fuel" value={v.fuelType} onChange={set("fuelType")} options={["PETROL", "DIESEL", "HYBRID", "ELECTRIC"].map((x) => ({ value: x, label: label(x) }))}/>
    </div>
    <div className="field-row">
      <Field label="Seats" type="number" min={1} max={60} value={v.seats} onChange={(e) => set("seats")(e.target.value)} error={f.errors.seats}/>
      <Select label={editing ? "Status" : "Initial status"} value={v.vehicleStatus} onChange={set("vehicleStatus")} options={statusOptions} error={f.errors.vehicleStatus}/>
    </div>
    <Select label="Home branch" icon="location" value={v.branchId} onChange={set("branchId")} options={[{ value: "", label: "Not assigned" }, ...branchOptions(branches)]}/>
    <Field label="Photo URL (optional)" placeholder="https://…" value={v.imageUrl} onChange={(e) => set("imageUrl")(e.target.value)} error={f.errors.imageUrl} hint="Leave empty to use a photo for the category, e.g. /vehicles/toyota-rav4.jpg."/>
  </Drawer>;
}

export function CustomerDrawer({ customer, close, saved }: { customer: Customer | null; close: () => void; saved: (c: Customer) => void }) {
  const { toast } = useFeedback();
  const editing = customer !== null;
  const f = useForm({
    fullName: customer?.fullName ?? "",
    email: customer?.email ?? "",
    phoneNumber: customer?.phoneNumber ?? "",
    driverLicenseNumber: customer?.driverLicenseNumber ?? "",
  });
  const v = f.values;

  const submit = () => {
    const body: CustomerInput = {
      fullName: v.fullName.trim(), email: v.email.trim(), phoneNumber: v.phoneNumber.trim() || null,
      driverLicenseNumber: v.driverLicenseNumber.trim().toUpperCase(),
    };
    const local: Record<string, string> = {};
    if (!body.fullName) local.fullName = "Full name is required";
    if (!/^\S+@\S+\.\S+$/.test(body.email)) local.email = "Email address is not valid";
    if (body.phoneNumber && !/^\+?[0-9 ]{9,16}$/.test(body.phoneNumber)) local.phoneNumber = "Phone number is not valid, e.g. +250 788 123 456";
    if (!/^DL-[A-Z0-9-]+$/.test(body.driverLicenseNumber)) local.driverLicenseNumber = "Driver License must start with 'DL-', e.g. DL-48219";
    f.run(local, async () => {
      const result = editing ? await api.updateCustomer(customer.customerId, body) : await api.createCustomer(body);
      toast(editing ? `${result.fullName}'s profile updated` : `${result.fullName} registered`);
      saved(result);
    });
  };

  return <Drawer eyebrow="Customer directory" title={editing ? "Edit customer" : "Register customer"} description={editing ? "Update this customer's profile." : "Create a new customer profile."}
    close={close} onSubmit={submit} busy={f.busy} submitLabel="Save customer" formError={f.formError}>
    <Field label="Full name" placeholder="Customer's full name" value={v.fullName} onChange={(e) => f.setValues({ ...v, fullName: e.target.value })} error={f.errors.fullName} autoFocus/>
    <Field label="Email address" type="email" placeholder="name@email.com" value={v.email} onChange={(e) => f.setValues({ ...v, email: e.target.value })} error={f.errors.email}
      disabled={customer?.hasAccount} hint={customer?.hasAccount ? "This customer signs in with this email, so it can't be changed here." : undefined}/>
    <Field label="Phone number" type="tel" placeholder="+250 7XX XXX XXX" value={v.phoneNumber} onChange={(e) => f.setValues({ ...v, phoneNumber: e.target.value })} error={f.errors.phoneNumber}/>
    <Field label="Driver license number" placeholder="DL-XXXXX" value={v.driverLicenseNumber} onChange={(e) => f.setValues({ ...v, driverLicenseNumber: e.target.value.toUpperCase() })} error={f.errors.driverLicenseNumber} hint="Use the format shown on the customer's license, starting with DL-."/>
  </Drawer>;
}

export function ContractModal({ close, issued }: { close: () => void; issued: () => void }) {
  const { toast } = useFeedback();
  const [customers, setCustomers] = useState<Customer[] | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const branches = useBranches();
  const f = useForm({ customerId: "", vehicleId: "", startDate: today(), days: "5", pickupBranchId: "" });
  const v = f.values;
  useEscape(useCallback(close, [close]));

  useEffect(() => {
    Promise.all([api.customers(), api.vehicles()])
      .then(([c, all]) => {
        setCustomers(c);
        const available = all.filter((x) => x.vehicleStatus === "AVAILABLE");
        setVehicles(available);
        f.setValues((cur) => ({ ...cur, customerId: c[0]?.customerId ?? "", vehicleId: available[0]?.vehicleId ?? "" }));
      })
      .catch((e) => setLoadError(e instanceof Error ? e.message : "Could not load customers and vehicles"));
  }, []);

  const vehicle = vehicles?.find((x) => x.vehicleId === v.vehicleId);
  const days = Number(v.days);
  const total = useMemo(() => (vehicle && days > 0 ? days * vehicle.dailyRate : 0), [vehicle, days]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!v.customerId) local.customerId = "Choose a customer";
    if (!v.vehicleId) local.vehicleId = "Choose a vehicle";
    if (!v.startDate) local.startDate = "Start date is required";
    if (!Number.isInteger(days) || days < 1 || days > 90) local.endDate = "Rental days must be between 1 and 90";
    f.run(local, async () => {
      const c = await api.issueContract({ customerId: v.customerId, vehicleId: v.vehicleId, startDate: v.startDate, endDate: addDays(v.startDate, days), pickupBranchId: v.pickupBranchId || undefined });
      toast(`Contract issued to ${c.customer.fullName}. ${plate(c.vehicle.plateNumber)} is now rented.`);
      issued();
    });
  };

  return <div className="modal-overlay" onMouseDown={close}>
    <form className="modal-card" role="dialog" aria-modal="true" aria-labelledby="contract-title" onMouseDown={(e) => e.stopPropagation()} onSubmit={submit} noValidate>
      <div className="modal-head"><div><span className="eyebrow">Rental contracts</span><h2 id="contract-title">Issue new contract</h2><p>Match a customer with an available vehicle.</p></div><button type="button" onClick={close} aria-label="Close"><Icon name="close"/></button></div>
      <div className="modal-body">
        {loadError && <p className="form-error">{loadError}</p>}
        <Select label="Customer" icon="users" value={v.customerId} onChange={(customerId) => f.setValues({ ...v, customerId })} error={f.errors.customerId}
          placeholder={customers === null ? "Loading…" : customers.length ? undefined : "No customers yet. Register one first."}
          options={(customers ?? []).map((c) => ({ value: c.customerId, label: `${c.fullName} · ${c.driverLicenseNumber}` }))}/>
        <Select label="Available vehicle" icon="car" value={v.vehicleId} onChange={(vehicleId) => f.setValues({ ...v, vehicleId })} error={f.errors.vehicleId}
          placeholder={vehicles === null ? "Loading…" : vehicles.length ? undefined : "No vehicles are available right now"}
          options={(vehicles ?? []).map((x) => ({ value: x.vehicleId, label: `${x.model} · ${plate(x.plateNumber)} · ${rwf(x.dailyRate)}/day` }))}/>
        <div className="field-row">
          <Field label="Start date" type="date" value={v.startDate} onChange={(e) => f.setValues({ ...v, startDate: e.target.value })} error={f.errors.startDate}/>
          <Field label="Rental days" type="number" min={1} max={90} value={v.days} onChange={(e) => f.setValues({ ...v, days: e.target.value })} error={f.errors.endDate}
            hint={days > 0 && v.startDate ? `Return on ${addDays(v.startDate, days)}` : undefined}/>
        </div>
        <Select label="Pickup branch" icon="location" value={v.pickupBranchId} onChange={(pickupBranchId) => f.setValues({ ...v, pickupBranchId })}
          options={[{ value: "", label: vehicle?.branch ? `Vehicle's home branch (${vehicle.branch.name})` : "Vehicle's home branch" }, ...branchOptions(branches)]}/>
        <div className="calculation"><div><span>Live calculation</span><small>{days > 0 ? days : 0} day{days === 1 ? "" : "s"} × {rwf(vehicle?.dailyRate ?? 0)}</small></div><strong>{rwf(total)}</strong></div>
        {f.formError && <p className="form-error" role="alert">{f.formError}</p>}
        <div className="notice"><span>i</span><p>Executing this contract will update the vehicle status from <strong>Available</strong> to <strong>Rented</strong>.</p></div>
      </div>
      <div className="modal-foot"><Button variant="outline" onClick={close}>Cancel</Button><Button type="submit" busy={f.busy} disabled={!customers?.length || !vehicles?.length}>Execute contract <Icon name="arrow" size={17}/></Button></div>
    </form>
  </div>;
}

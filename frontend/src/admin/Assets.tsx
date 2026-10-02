import { useEffect, useMemo, useState } from "react";
import { api, errorMessage, type Vehicle } from "../api";
import { Button, DataTable, EmptyState, ErrorBanner, Icon, Loading, RowMenu, StatusPill, TableFooter, useFeedback, usePaged } from "../components/ui";
import { CATEGORIES, label, plate, rwf, shortId } from "../format";
import { AdminPageHeader, AdminSearch, matches, useAdmin } from "./AdminShell";
import { VehicleDrawer } from "./forms";

export default function Assets() {
  const { query, refreshCounts } = useAdmin();
  const { toast, confirm } = useFeedback();
  const [vehicles, setVehicles] = useState<Vehicle[] | null>(null);
  const [error, setError] = useState("");
  const [category, setCategory] = useState("");
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState<Vehicle | null | undefined>(undefined);

  const load = () => {
    setError("");
    api.vehicles().then(setVehicles).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  const rows = useMemo(() => (vehicles ?? []).filter((v) =>
    (!category || v.category === category) && (!status || v.vehicleStatus === status) &&
    matches(query, v.model, v.plateNumber, shortId("VEH", v.vehicleId))), [vehicles, category, status, query]);
  const paged = usePaged(rows, 10);

  const setVehicleStatus = async (v: Vehicle, next: "AVAILABLE" | "MAINTENANCE") => {
    try {
      await api.updateVehicle(v.vehicleId, { ...v, vehicleStatus: next });
      toast(`${plate(v.plateNumber)} is now ${label(next).toLowerCase()}`);
      load();
      refreshCounts();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const remove = async (v: Vehicle) => {
    const ok = await confirm({
      title: "Delete this vehicle?",
      message: <><strong>{v.model}</strong> ({plate(v.plateNumber)}) and its past contract history will be permanently removed. This can't be undone.</>,
      confirmLabel: "Delete vehicle",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteVehicle(v.vehicleId);
      toast(`${v.model} removed from the fleet`);
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  return <>
    <AdminPageHeader title="Every vehicle, one clear view." description="Manage availability, rates, and fleet details.">
      <AdminSearch placeholder="Filter by plate or model…"/>
      <label className="select-button"><select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Category">
        <option value="">All categories</option>{CATEGORIES.map((c) => <option key={c} value={c}>{label(c)}</option>)}
      </select><Icon name="chevron" size={16}/></label>
      <label className="select-button"><select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
        <option value="">Any status</option>{["AVAILABLE", "RENTED", "RESERVED", "MAINTENANCE"].map((s) => <option key={s} value={s}>{label(s)}</option>)}
      </select><Icon name="chevron" size={16}/></label>
      <Button onClick={() => setEditing(null)}><Icon name="plus" size={17}/> Add new vehicle</Button>
    </AdminPageHeader>
    {error && <ErrorBanner message={error} onRetry={load}/>}
    <section className="admin-card">
      {vehicles === null && !error ? <Loading/> : <>
        <DataTable headers={["Vehicle ID", "Plate number", "Model", "Category", "Daily rate", "Status", ""]}
          empty={rows.length === 0 && <EmptyState title={vehicles?.length ? "No vehicles match" : "No vehicles yet"} text={vehicles?.length ? "Try a different search or filter." : "Add your first vehicle to start renting."}/>}>
          {paged.slice.map((v) => <tr key={v.vehicleId}>
            <td className="muted">{shortId("VEH", v.vehicleId)}</td>
            <td><span className="plate">{plate(v.plateNumber)}</span></td>
            <td><strong>{v.model}</strong></td>
            <td>{label(v.category)}</td>
            <td><strong>{rwf(v.dailyRate)}</strong></td>
            <td><StatusPill status={v.vehicleStatus}/></td>
            <td className="actions"><RowMenu actions={[
              { label: "Edit details", onSelect: () => setEditing(v) },
              v.vehicleStatus === "MAINTENANCE"
                ? { label: "Mark available", onSelect: () => setVehicleStatus(v, "AVAILABLE") }
                : { label: "Send to maintenance", onSelect: () => setVehicleStatus(v, "MAINTENANCE"), disabled: v.vehicleStatus !== "AVAILABLE" },
              { label: "Delete vehicle", onSelect: () => remove(v), danger: true },
            ]}/></td>
          </tr>)}
        </DataTable>
        <TableFooter shown={paged.slice.length} total={rows.length} noun="vehicles" {...paged}/>
      </>}
    </section>
    {editing !== undefined && <VehicleDrawer vehicle={editing} close={() => setEditing(undefined)} saved={() => { setEditing(undefined); load(); }}/>}
  </>;
}

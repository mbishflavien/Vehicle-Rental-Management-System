import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, errorMessage, type DashboardStats } from "../api";
import { ButtonLink, DataTable, EmptyState, ErrorBanner, Icon, Loading } from "../components/ui";
import { plate, relativeTime, rwf, rwfCompact } from "../format";

function KpiCard({ icon, label, value, trend, note, positive = true }: { icon: string; label: string; value: string; trend?: string; note: string; positive?: boolean }) {
  return <article className="kpi-card">
    <div className="kpi-top"><span><Icon name={icon}/></span><small>Last 30 days</small></div>
    <p>{label}</p>
    <h2>{value}</h2>
    <div className="kpi-trend">{trend && <b className={positive ? "" : "down"}><Icon name="trend" size={14}/>{trend}</b>}<span>{note}</span></div>
  </article>;
}

const longDate = new Intl.DateTimeFormat("en-GB", { weekday: "long", month: "long", day: "numeric" });

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [error, setError] = useState("");

  const load = () => {
    setError("");
    api.dashboard().then(setStats).catch((e) => setError(errorMessage(e)));
  };
  useEffect(load, []);

  if (error) return <ErrorBanner message={error} onRetry={load}/>;
  if (!stats) return <Loading/>;

  const trend = stats.revenueTrendPercent;
  const healthy = stats.maintenanceVehicles === 0 && stats.pendingContracts === 0;
  const headline = stats.totalVehicles === 0
    ? "Add your first vehicle to get started."
    : healthy ? "Your fleet is running smoothly."
    : stats.pendingContracts > 0 ? `${stats.pendingContracts} booking${stats.pendingContracts === 1 ? "" : "s"} waiting for your approval.`
    : `${stats.maintenanceVehicles} vehicle${stats.maintenanceVehicles === 1 ? " is" : "s are"} in maintenance.`;

  return <>
    <div className="dashboard-welcome">
      <div><span className="eyebrow">{longDate.format(new Date()).replace(",", " ·")}</span><h2>{headline}</h2></div>
      <ButtonLink to="/admin/contracts?issue=1"><Icon name="plus" size={17}/> Issue new contract</ButtonLink>
    </div>
    <div className="kpi-grid">
      <KpiCard icon="trend" label="Total fleet revenue" value={rwfCompact(stats.revenueLast30Days)}
        trend={trend == null ? undefined : `${trend >= 0 ? "+" : ""}${trend.toFixed(1)}%`} positive={(trend ?? 0) >= 0}
        note={trend == null ? "active & completed rentals" : "from previous 30 days"}/>
      <KpiCard icon="file" label="Active contracts" value={String(stats.activeContracts)}
        trend={stats.newContractsLast30Days ? `+${stats.newContractsLast30Days}` : undefined} note={stats.newContractsLast30Days ? "issued this period" : "vehicles on the road"}/>
      <KpiCard icon="car" label="Fleet utilization" value={`${stats.utilizationPercent.toFixed(1)}%`}
        note={`${stats.rentedVehicles} of ${stats.totalVehicles} vehicles rented`}/>
      <KpiCard icon="dashboard" label="Available fleet" value={String(stats.availableVehicles)}
        trend={stats.maintenanceVehicles ? `${stats.maintenanceVehicles} in maintenance` : undefined} positive={!stats.maintenanceVehicles}
        note="vehicles today"/>
    </div>
    <section className="admin-card activity-card">
      <div className="card-heading"><div><h2>Recent activity</h2><p>Latest events from across your operations.</p></div><ButtonLink variant="ghost" to="/admin/logs">View all activity <Icon name="arrow" size={16}/></ButtonLink></div>
      <DataTable headers={["Time", "Customer", "Plate number", "Amount", "Event"]}
        empty={stats.recentActivity.length === 0 && <EmptyState title="No activity yet" text="Contracts and bookings will appear here."><Link to="/admin/contracts?issue=1">Issue the first contract</Link></EmptyState>}>
        {stats.recentActivity.map((r) => <tr key={r.logId}>
          <td><span className="muted">{relativeTime(r.timestamp)}</span></td>
          <td><strong>{r.customerName}</strong></td>
          <td><span className="plate">{plate(r.plateNumber ?? "")}</span></td>
          <td><strong>{r.amount == null ? "—" : rwf(r.amount)}</strong></td>
          <td><span className="event-dot">{r.event}</span></td>
        </tr>)}
      </DataTable>
    </section>
  </>;
}

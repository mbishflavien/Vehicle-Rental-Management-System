import type { ContractStatus, Vehicle, VehicleCategory, VehicleStatus } from "./api";

export const images = {
  hero: "https://images.unsplash.com/photo-1783557105881-a19f1dd0be95?auto=format&fit=crop&w=2000&q=88",
  road: "https://images.unsplash.com/photo-1682773083896-95176d8aecf8?auto=format&fit=crop&w=1400&q=86",
  suv: "https://images.unsplash.com/photo-1773423203025-d6060d1e5a8c?auto=format&fit=crop&w=1200&q=86",
  scenic: "https://images.unsplash.com/photo-1786702885812-6ca6d1105778?auto=format&fit=crop&w=1200&q=86",
};

const categoryImage: Record<VehicleCategory, string> = {
  SUV: images.suv, SEDAN: images.road, HATCHBACK: images.scenic, VAN: images.road, COMMERCIAL: images.suv,
};

export function vehicleImage(v: Vehicle): string {
  return v.imageUrl || (v.category ? categoryImage[v.category] : images.hero);
}

export const CATEGORIES: VehicleCategory[] = ["SUV", "SEDAN", "HATCHBACK", "VAN", "COMMERCIAL"];

const nf = new Intl.NumberFormat("en-US");

export const rwf = (n: number | null | undefined) => `RWF ${nf.format(Math.round(n ?? 0))}`;

/** Compact money for KPI cards: RWF 18.4M, RWF 425K. */
export function rwfCompact(n: number): string {
  if (n >= 1_000_000) return `RWF ${(n / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
  if (n >= 10_000) return `RWF ${Math.round(n / 1_000)}K`;
  return rwf(n);
}

/** "RAB123A" -> "RAB 123 A" */
export function plate(p: string): string {
  const m = /^([A-Z]{3})(\d{3})([A-Z])$/.exec(p);
  return m ? `${m[1]} ${m[2]} ${m[3]}` : p;
}

/** Short human-friendly IDs derived from the UUIDs, e.g. VEH-93A1. */
export const shortId = (prefix: string, uuid: string) => `${prefix}-${uuid.replace(/-/g, "").slice(0, 4).toUpperCase()}`;

/** "SUV" stays, "SEDAN" -> "Sedan", "AVAILABLE" -> "Available" */
export function label(e: string | null | undefined): string {
  if (!e) return "—";
  if (e === "SUV") return e;
  return e.charAt(0) + e.slice(1).toLowerCase();
}

export function specs(v: Vehicle): string {
  return [v.transmission && label(v.transmission), v.seats && `${v.seats} seats`, v.fuelType && label(v.fuelType)]
    .filter(Boolean)
    .join(" · ") || "Details on request";
}

export const statusLabel = (s: VehicleStatus | ContractStatus) => label(s);

// --- dates ---------------------------------------------------------------------------------------

/** Local date as yyyy-mm-dd (what <input type="date"> and the API use). */
export function isoDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return isoDate(d);
}

export function daysBetween(start: string, end: string): number {
  const ms = new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime();
  return Math.round(ms / 86_400_000);
}

export const today = () => isoDate(new Date());

const dayMonth = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const dayMonthYear = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

/** "12–17 Jun 2025", or "28 Jun – 3 Jul 2025" across months. */
export function period(start: string, end: string): string {
  const s = new Date(`${start}T00:00:00`);
  const e = new Date(`${end}T00:00:00`);
  if (s.getFullYear() === e.getFullYear() && s.getMonth() === e.getMonth()) {
    return `${s.getDate()}–${dayMonthYear.format(e)}`;
  }
  if (s.getFullYear() === e.getFullYear()) return `${dayMonth.format(s)} – ${dayMonthYear.format(e)}`;
  return `${dayMonthYear.format(s)} – ${dayMonthYear.format(e)}`;
}

const time = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });
const timeSeconds = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

/** "Today, 09:42", "Yesterday, 17:34" or "12 Jun, 08:10" */
export function relativeTime(instant: string): string {
  const d = new Date(instant);
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const diffDays = Math.floor((startOfToday.getTime() - new Date(d).setHours(0, 0, 0, 0)) / 86_400_000);
  const prefix = diffDays === 0 ? "Today" : diffDays === 1 ? "Yesterday" : dayMonth.format(d);
  return `${prefix}, ${time.format(d)}`;
}

export function logTime(instant: string): { date: string; time: string } {
  const d = new Date(instant);
  return { date: dayMonthYear.format(d), time: timeSeconds.format(d) };
}

export function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

export const initials = (name: string) =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join("");

export const documentTypeLabel = (t: string) =>
  ({ DRIVER_LICENSE: "Driver license", NATIONAL_ID: "National ID", PASSPORT: "Passport", OTHER: "Other document" } as Record<string, string>)[t] ?? t;

export const fileSize = (bytes: number) =>
  bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;

// --- CSV -----------------------------------------------------------------------------------------

export function downloadCsv(filename: string, headers: string[], rows: (string | number | null | undefined)[][]) {
  const cell = (v: string | number | null | undefined) => {
    const s = v == null ? "" : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const csv = [headers, ...rows].map((r) => r.map(cell).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

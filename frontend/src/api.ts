// Typed client for the Spring Boot REST API. Every error from the API has the shape
// { status, message, fieldErrors: { field: message }, timestamp } (see GlobalExceptionHandler).

export type Role = "ADMIN" | "CUSTOMER";
export type VehicleStatus = "AVAILABLE" | "RENTED" | "MAINTENANCE" | "RESERVED";
export type ContractStatus = "PENDING" | "ACTIVE" | "COMPLETED" | "CANCELLED";
export type VehicleCategory = "SUV" | "SEDAN" | "HATCHBACK" | "VAN" | "COMMERCIAL";
export type Transmission = "AUTOMATIC" | "MANUAL";
export type FuelType = "PETROL" | "DIESEL" | "HYBRID" | "ELECTRIC";

export interface UserView {
  userId: string;
  fullName: string;
  email: string;
  role: Role;
  jobTitle: string | null;
  customerId: string | null;
}

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  user: UserView;
}

export interface Vehicle {
  vehicleId: string;
  plateNumber: string;
  model: string;
  dailyRate: number;
  vehicleStatus: VehicleStatus;
  category: VehicleCategory | null;
  transmission: Transmission | null;
  fuelType: FuelType | null;
  seats: number | null;
  imageUrl: string | null;
  createdAt: string;
}

export type VehicleInput = Omit<Vehicle, "vehicleId" | "createdAt">;

export interface Customer {
  customerId: string;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  driverLicenseNumber: string;
  hasAccount: boolean;
  createdAt: string;
}

export type CustomerInput = Pick<Customer, "fullName" | "email" | "phoneNumber" | "driverLicenseNumber">;

export interface Contract {
  contractId: string;
  startDate: string;
  endDate: string;
  totalCost: number;
  contractStatus: ContractStatus;
  pickupLocation: string | null;
  customer: Customer;
  vehicle: Vehicle;
  issuedByName: string | null;
  createdAt: string;
}

export interface AuditLog {
  logId: string;
  timestamp: string;
  event: string;
  actor: string;
  details: string | null;
  amount: number | null;
  plateNumber: string | null;
  customerName: string | null;
}

export interface DashboardStats {
  revenueLast30Days: number;
  revenueTrendPercent: number | null;
  activeContracts: number;
  newContractsLast30Days: number;
  utilizationPercent: number;
  rentedVehicles: number;
  availableVehicles: number;
  totalVehicles: number;
  maintenanceVehicles: number;
  pendingContracts: number;
  totalCustomers: number;
  recentActivity: AuditLog[];
}

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const TOKEN_KEY = "vrms.token";

export const tokenStore = {
  get: (): string | null => {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set: (token: string | null) => {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch { /* storage unavailable: the session just won't survive a reload */ }
  },
};

/** Called when the API says the token is no longer valid, so the app can sign the user out. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) { onUnauthorized = handler; }

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let res: Response;
  try {
    res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, "Can't reach the VRMS server. Check that the backend is running.");
  }

  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && token) onUnauthorized();
    throw new ApiError(res.status, data?.message ?? `Request failed (${res.status})`, data?.fieldErrors ?? {});
  }
  return data as T;
}

export const api = {
  login: (email: string, password: string) => request<AuthResponse>("POST", "/auth/login", { email, password }),
  register: (body: { fullName: string; email: string; phoneNumber: string; driverLicenseNumber: string; password: string }) =>
    request<AuthResponse>("POST", "/auth/register", body),
  me: () => request<UserView>("GET", "/auth/me"),

  vehicles: () => request<Vehicle[]>("GET", "/vehicles"),
  vehicle: (id: string) => request<Vehicle>("GET", `/vehicles/${id}`),
  createVehicle: (v: VehicleInput) => request<Vehicle>("POST", "/vehicles", v),
  updateVehicle: (id: string, v: VehicleInput) => request<Vehicle>("PUT", `/vehicles/${id}`, v),
  deleteVehicle: (id: string) => request<void>("DELETE", `/vehicles/${id}`),

  customers: () => request<Customer[]>("GET", "/customers"),
  createCustomer: (c: CustomerInput) => request<Customer>("POST", "/customers", c),
  updateCustomer: (id: string, c: CustomerInput) => request<Customer>("PUT", `/customers/${id}`, c),
  deleteCustomer: (id: string) => request<void>("DELETE", `/customers/${id}`),

  contracts: () => request<Contract[]>("GET", "/contracts"),
  issueContract: (body: { customerId: string; vehicleId: string; startDate: string; endDate: string; pickupLocation?: string }) =>
    request<Contract>("POST", "/contracts", body),
  setContractStatus: (id: string, status: ContractStatus) => request<Contract>("PATCH", `/contracts/${id}/status`, { status }),
  deleteContract: (id: string) => request<void>("DELETE", `/contracts/${id}`),

  myBookings: () => request<Contract[]>("GET", "/me/bookings"),
  book: (body: { vehicleId: string; startDate: string; endDate: string; pickupLocation?: string }) =>
    request<Contract>("POST", "/me/bookings", body),
  cancelBooking: (id: string) => request<Contract>("POST", `/me/bookings/${id}/cancel`),

  dashboard: () => request<DashboardStats>("GET", "/dashboard"),
  logs: (limit = 500) => request<AuditLog[]>("GET", `/logs?limit=${limit}`),
};

/** Message for a caught error, whatever threw it. */
export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : "Something went wrong";
}

import { useEffect, type ReactNode } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import AdminShell from "./admin/AdminShell";
import Assets from "./admin/Assets";
import Contracts from "./admin/Contracts";
import Customers from "./admin/Customers";
import Dashboard from "./admin/Dashboard";
import Logs from "./admin/Logs";
import { AuthProvider, useAuth } from "./auth";
import { FeedbackProvider, Loading } from "./components/ui";
import type { Role } from "./api";
import AuthPage from "./pages/Auth";
import FleetPage from "./pages/Fleet";
import HomePage from "./pages/Home";
import MyBookingsPage from "./pages/MyBookings";

/** Sends signed-out visitors to sign in (and back afterwards); other roles go to their own home. */
function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="page-loading"><Loading/></div>;
  if (!user) return <Navigate to={`/signin?next=${encodeURIComponent(location.pathname + location.search)}`} replace/>;
  if (user.role !== role) return <Navigate to={user.role === "ADMIN" ? "/admin" : "/fleet"} replace/>;
  return <>{children}</>;
}

/** Scroll to the top on navigation, or to #section links such as /#solutions. */
function ScrollManager() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (hash) {
      const el = document.getElementById(hash.slice(1));
      if (el) { el.scrollIntoView({ behavior: "smooth" }); return; }
    }
    window.scrollTo({ top: 0 });
  }, [pathname, hash]);
  return null;
}

export default function App() {
  return <BrowserRouter>
    <AuthProvider>
      <FeedbackProvider>
        <ScrollManager/>
        <Routes>
          <Route path="/" element={<HomePage/>}/>
          <Route path="/fleet" element={<FleetPage/>}/>
          <Route path="/signin" element={<AuthPage/>}/>
          <Route path="/account/bookings" element={<RequireRole role="CUSTOMER"><MyBookingsPage/></RequireRole>}/>
          <Route path="/admin" element={<RequireRole role="ADMIN"><AdminShell/></RequireRole>}>
            <Route index element={<Dashboard/>}/>
            <Route path="fleet" element={<Assets/>}/>
            <Route path="customers" element={<Customers/>}/>
            <Route path="contracts" element={<Contracts/>}/>
            <Route path="logs" element={<Logs/>}/>
          </Route>
          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
      </FeedbackProvider>
    </AuthProvider>
  </BrowserRouter>;
}

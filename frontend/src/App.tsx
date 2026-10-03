import { lazy, Suspense, useEffect, type ReactNode } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AuthProvider, isStaff, useAuth } from "./auth";

// The staff console is only downloaded when someone opens it, keeping the public site light.
const AdminShell = lazy(() => import("./admin/AdminShell"));
const Assets = lazy(() => import("./admin/Assets"));
const Contracts = lazy(() => import("./admin/Contracts"));
const Customers = lazy(() => import("./admin/Customers"));
const Dashboard = lazy(() => import("./admin/Dashboard"));
const Logs = lazy(() => import("./admin/Logs"));
const Notifications = lazy(() => import("./admin/Notifications"));
const Staff = lazy(() => import("./admin/Staff"));
import { FeedbackProvider, Loading } from "./components/ui";
import type { Role } from "./api";
import AuthPage from "./pages/Auth";
import CompleteProfilePage from "./pages/CompleteProfile";
import OAuthCallback from "./pages/OAuthCallback";
import FleetPage from "./pages/Fleet";
import HomePage from "./pages/Home";
import MyBookingsPage from "./pages/MyBookings";

/** Sends signed-out visitors to sign in (and back afterwards); other roles go to their own home. */
function RequireRole({ roles, children, needsProfile = false }: { roles: Role[]; children: ReactNode; needsProfile?: boolean }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="page-loading"><Loading/></div>;
  if (!user) return <Navigate to={`/signin?next=${encodeURIComponent(location.pathname + location.search)}`} replace/>;
  if (!roles.includes(user.role)) return <Navigate to={isStaff(user) ? "/admin" : "/fleet"} replace/>;
  if (needsProfile && user.role === "CUSTOMER" && !user.customerId) {
    return <Navigate to={`/account/profile?next=${encodeURIComponent(location.pathname)}`} replace/>;
  }
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
        <Suspense fallback={<div className="page-loading"><Loading/></div>}>
        <Routes>
          <Route path="/" element={<HomePage/>}/>
          <Route path="/fleet" element={<FleetPage/>}/>
          <Route path="/signin" element={<AuthPage/>}/>
          <Route path="/account/bookings" element={<RequireRole roles={["CUSTOMER"]} needsProfile><MyBookingsPage/></RequireRole>}/>
          <Route path="/account/profile" element={<RequireRole roles={["CUSTOMER"]}><CompleteProfilePage/></RequireRole>}/>
          <Route path="/oauth/callback" element={<OAuthCallback/>}/>
          <Route path="/admin" element={<RequireRole roles={["ADMIN", "AGENT"]}><AdminShell/></RequireRole>}>
            <Route index element={<Dashboard/>}/>
            <Route path="fleet" element={<Assets/>}/>
            <Route path="customers" element={<Customers/>}/>
            <Route path="contracts" element={<Contracts/>}/>
            <Route path="logs" element={<Logs/>}/>
            <Route path="notifications" element={<Notifications/>}/>
            <Route path="staff" element={<Staff/>}/>
          </Route>
          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
        </Suspense>
      </FeedbackProvider>
    </AuthProvider>
  </BrowserRouter>;
}

import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { errorMessage } from "../api";
import { isStaff, useAuth } from "../auth";
import { Loading, Wordmark } from "../components/ui";

export const OAUTH_NEXT_KEY = "vrms.oauth.next";

/**
 * Landing page after "Continue with Google / GitHub". The backend puts the VRMS access token in the
 * URL fragment (#token=…), which browsers never send to a server.
 */
export default function OAuthCallback() {
  const { signInWithToken } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const done = useRef(false);

  useEffect(() => {
    if (done.current) return;
    done.current = true;
    const token = new URLSearchParams(window.location.hash.slice(1)).get("token");
    window.history.replaceState(null, "", window.location.pathname); // drop the token from the address bar
    let next: string | null = null;
    try { next = sessionStorage.getItem(OAUTH_NEXT_KEY); sessionStorage.removeItem(OAUTH_NEXT_KEY); } catch { /* ignore */ }
    if (!token) { setError("Sign-in didn't complete. Please try again."); return; }

    signInWithToken(token)
      .then((user) => {
        if (user.role === "CUSTOMER" && !user.customerId) {
          navigate(`/account/profile${next ? `?next=${encodeURIComponent(next)}` : ""}`, { replace: true });
        } else {
          navigate(next ?? (isStaff(user) ? "/admin" : "/fleet"), { replace: true });
        }
      })
      .catch((e) => setError(errorMessage(e)));
  }, [navigate, signInWithToken]);

  return <div className="page-loading"><div className="center">
    <Wordmark/>
    {error ? <><p className="form-error">{error}</p><Link to="/signin">Back to sign in</Link></> : <Loading label="Signing you in…"/>}
  </div></div>;
}

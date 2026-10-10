import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the React app runs on :5173 and forwards /api to Spring Boot on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Keep the browser's Host header so OAuth2 redirect URIs point back at :5173.
    proxy: {
      "/api": "http://localhost:8080",
      "/oauth2": { target: "http://localhost:8080", xfwd: true },
      "/login/oauth2": { target: "http://localhost:8080", xfwd: true },
    },
  },
});

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth";
import { Field, FeedbackProvider, RowMenu, StatusPill } from "../components/ui";
import AuthPage from "../pages/Auth";

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

function renderAuth(path = "/signin?tab=register") {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><FeedbackProvider><AuthPage/></FeedbackProvider></AuthProvider></MemoryRouter>);
}

describe("ui components", () => {
  it("shows a field's error and marks it invalid", () => {
    render(<Field label="Plate number" value="" onChange={() => {}} error="Plate number must follow Rwandan format"/>);
    expect(screen.getByText("Plate number must follow Rwandan format")).toBeInTheDocument();
    expect(screen.getByLabelText("Plate number")).toHaveAttribute("aria-invalid", "true");
  });

  it("renders status pills with readable text", () => {
    render(<StatusPill status="MAINTENANCE"/>);
    expect(screen.getByText("Maintenance")).toHaveClass("status-maintenance");
  });

  it("row menu runs the chosen action", async () => {
    const onDelete = vi.fn();
    render(<RowMenu actions={[{ label: "Edit", onSelect: () => {} }, { label: "Delete", onSelect: onDelete, danger: true }]}/>);
    await userEvent.click(screen.getByRole("button", { name: "More actions" }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Delete" }));
    expect(onDelete).toHaveBeenCalledOnce();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });
});

describe("registration form", () => {
  it("validates business rules before calling the API", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => jsonResponse([]));
    renderAuth();

    await userEvent.type(screen.getByLabelText("Full name"), "Aline Uwase");
    await userEvent.type(screen.getByLabelText("Email address"), "aline@email.com");
    await userEvent.type(screen.getByLabelText("Driver license number"), "48219");
    await userEvent.type(screen.getByLabelText("Password"), "password");
    await userEvent.click(screen.getByRole("button", { name: /Register & continue/ }));

    expect(screen.getByText("Driver License must start with 'DL-', e.g. DL-48219")).toBeInTheDocument();
    expect(screen.getByText("Password must contain letters and numbers")).toBeInTheDocument();
    expect(fetchMock.mock.calls.every(([url]) => !String(url).includes("/auth/register"))).toBe(true);
  });

  it("shows the server's field errors under the right input", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation((url) => String(url).includes("/auth/register")
      ? jsonResponse({ status: 409, message: "Conflict", fieldErrors: { email: "An account with this email already exists. Try signing in." } }, 409)
      : jsonResponse([]));
    renderAuth();

    await userEvent.type(screen.getByLabelText("Full name"), "Aline Uwase");
    await userEvent.type(screen.getByLabelText("Email address"), "aline@email.com");
    await userEvent.type(screen.getByLabelText("Driver license number"), "DL-48219");
    await userEvent.type(screen.getByLabelText("Password"), "secret-pass-1");
    await userEvent.click(screen.getByRole("button", { name: /Register & continue/ }));

    await waitFor(() => expect(screen.getByText("An account with this email already exists. Try signing in.")).toBeInTheDocument());
    expect(screen.getByLabelText("Email address")).toHaveAttribute("aria-invalid", "true");
  });

  it("offers Google and GitHub sign-in when the server enables them", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(() => jsonResponse(["google", "github"]));
    renderAuth("/signin");
    expect(await screen.findByRole("button", { name: /Continue with Google/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Continue with GitHub/ })).toBeInTheDocument();
  });
});

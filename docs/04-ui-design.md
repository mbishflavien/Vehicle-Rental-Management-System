# 4. UI design & responsive prototype

- **Design:** [Figma Make prototype](https://www.figma.com/make/9onglo1jX7XiV8dwmBgoU3/Vehicle-Rental-Management-System), implemented faithfully in React 19 + TypeScript (`frontend/`).
- **Visual language:** warm "Rwandan earth" palette (cream `#faf6f0`, espresso `#332b24`, terracotta `#c1622d`, sage and amber status colours), *DM Serif Display* headings with *DM Sans* body text, rounded cards and pill buttons. Colours and fonts are CSS variables in `frontend/src/index.css`.
- **Two experiences:** the public site (home, fleet, sign in, bookings, profile) and the staff console (sidebar + top bar, tables, drawers, modals).

## Responsive strategy

| Breakpoint | Layout changes |
|---|---|
| **> 1100 px** desktop | Full navigation; search card in one row; 3-column fleet grid; 4 KPI cards; sidebar with labels |
| **≤ 1100 px** tablet | Search card 2×2; KPI cards 2×2; page actions wrap under the title |
| **≤ 820 px** small tablet / large phone | Navigation collapses to a menu button; single-column fleet; staff sidebar shrinks to icons; tables scroll horizontally inside their card |
| **≤ 560 px** phone | Stacked forms and buttons; full-width actions; single KPI column |

Accessibility: every input has a real `<label>`, errors and hints are linked with `aria-describedby`
and set `aria-invalid`; dialogs close with Esc; menus are keyboard reachable; status is never shown by
colour alone (pills carry text).

## Screens

### Desktop (1440 × 900)

| | |
|---|---|
| **Home** (full page)<br/>![Home](screenshots/desktop-home.png) | **Fleet** with branch, type and sort filters<br/>![Fleet](screenshots/desktop-fleet.png) |
| **Sign in / register** (Google & GitHub when enabled)<br/>![Sign in](screenshots/desktop-signin.png) | **My bookings** with messages from VRMS<br/>![My bookings](screenshots/desktop-my-bookings.png) |
| **Profile & documents** (GridFS upload)<br/>![Profile](screenshots/desktop-profile-documents.png) | **Staff dashboard**<br/>![Dashboard](screenshots/desktop-admin-dashboard.png) |
| **Fleet assets**<br/>![Fleet assets](screenshots/desktop-admin-fleet.png) | **Add vehicle** drawer<br/>![Add vehicle](screenshots/desktop-admin-add-vehicle.png) |
| **Rental contracts**<br/>![Contracts](screenshots/desktop-admin-contracts.png) | **Issue contract** with live cost<br/>![Issue contract](screenshots/desktop-admin-issue-contract.png) |
| **Customer documents** review<br/>![Documents](screenshots/desktop-admin-customer-documents.png) | **Notifications** sent through RabbitMQ<br/>![Notifications](screenshots/desktop-admin-notifications.png) |
| **Email preview** (sandboxed)<br/>![Email](screenshots/desktop-admin-email-preview.png) | **System logs** (MongoDB)<br/>![Logs](screenshots/desktop-admin-logs.png) |
| **Staff & access** (RBAC)<br/>![Staff](screenshots/desktop-admin-staff.png) | **Role permissions** when adding staff<br/>![Roles](screenshots/desktop-admin-staff-roles.png) |
| **Agent view**: no logs, staff or delete actions<br/>![Agent](screenshots/desktop-agent-contracts.png) | **Mailpit inbox**: real emails sent by VRMS<br/>![Mailpit](screenshots/mailpit-inbox.png) |

### Tablet (834 × 1112)

| Home | Fleet | Staff dashboard |
|---|---|---|
| ![Tablet home](screenshots/tablet-home.png) | ![Tablet fleet](screenshots/tablet-fleet.png) | ![Tablet dashboard](screenshots/tablet-admin-dashboard.png) |

### Phone (390 × 844)

| Home | Menu | Fleet | Booking | My bookings | Staff contracts |
|---|---|---|---|---|---|
| ![](screenshots/mobile-home.png) | ![](screenshots/mobile-menu.png) | ![](screenshots/mobile-fleet.png) | ![](screenshots/mobile-booking.png) | ![](screenshots/mobile-my-bookings.png) | ![](screenshots/mobile-admin-contracts.png) |

## From prototype to product

The Figma prototype was static (hard-coded data, buttons without actions). The implementation keeps
its layout and styling and adds what a working system needs: real data from the API, loading and empty
states, inline validation and server errors, confirmation dialogs, toasts, pagination, role-aware
navigation, the customer booking flow and profile, staff management, notifications and documents.
Problems found while testing on devices (a merged word in the mobile hero, wrapping totals) were fixed.

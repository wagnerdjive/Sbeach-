# South Beach Reservations API

Spring Boot REST API for reservation requests, built for Java 17. Public customers can request a reservation; an authenticated South Beach team member can review requests and mark them confirmed or cancelled.

## Run locally

Requirements: Java 17 and Maven 3.6.3+.

```sh
cd backend
export APP_ADMIN_PASSWORD='replace-this-with-a-unique-secret-of-at-least-16-characters'
mvn spring-boot:run
```

The default local database is an H2 file at `backend/data/southbeach-reservations`. It keeps local requests between app restarts. The app refuses to start if `APP_ADMIN_PASSWORD` is missing or shorter than 16 characters.

Check `GET http://localhost:8080/api/health`. The static site served at `http://localhost:8000` is configured to submit to this local API automatically. For a different API origin, set `window.SOUTH_BEACH_API_BASE` in `api-config.js` and add the website origin to `CORS_ALLOWED_ORIGINS`.

## API

### Public

`POST /api/reservations` validates and saves a request as `PENDING`, returning a public reference. It does not promise availability or confirm a table.

```json
{
  "fullName": "Ana Cossa",
  "phone": "+258 84 123 4567",
  "requestedDate": "2026-12-05",
  "requestedTime": "19:00",
  "partySize": 4,
  "venue": "RESTAURANT",
  "occasion": "Aniversário",
  "notes": "Mesa exterior, se possível"
}
```

Venue values: `RESTAURANT`, `BEACH_BAR`, `SPORTS_BAR`, `NO_PREFERENCE`. Dates before the current Maputo date are rejected. Names, phone numbers, group size and text fields are validated server-side.

### Team only (HTTP Basic)

- `GET /api/admin/reservations` — paginated requests, newest first; optionally filter with `?status=PENDING`.
- `PATCH /api/admin/reservations/{reference}/status` — set `{"status":"CONFIRMED"}` or `{"status":"CANCELLED"}`. Confirming checks capacity and returns `409` if the venue is full; a `NO_PREFERENCE` request needs `"venue"` (e.g. `{"status":"CONFIRMED","venue":"BEACH_BAR"}`).

### Notifications

Confirming or cancelling a reservation, and a reminder the day before a confirmed one (daily at 10:00 Maputo time, once per reservation), trigger customer messages after the change is committed; a delivery failure never blocks the change. The form's email field is optional.

- Email: set `NOTIFY_EMAIL_ENABLED=true`, `NOTIFY_EMAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` (any SMTP provider).
- SMS: no provider is wired yet. Register a bean implementing `SmsGateway`; without one SMS is skipped.
- `NOTIFY_REMINDERS_ENABLED` and `NOTIFY_REMINDERS_CRON` control reminders. Messages are in Portuguese only.

### Staff dashboard

`admin.html` (not linked from the public menu, `noindex`) has three tabs. *Reservas* lists requests, filters by status and confirms or cancels them. *Eventos e bilhetes* creates and edits events (draft, published, cancelled) and their ticket types (price in MZN, capacity, per-order limit, sale window), showing sold, held and available stock; dates are entered in Maputo time. *Encomendas* lists ticket orders and cancels pending ones, which releases their stock. Everything goes through the admin API. Staff sign in with the `APP_ADMIN_*` credentials; they are kept in memory only, so reloading the page signs out. Locally open `http://localhost:8000/admin.html` with the API running.

### Availability

Capacity per venue lives in the `venue_capacity` table. The seeded values (Restaurante 60, Beach Bar 80, Sports Bar 50) are **placeholders**; South Beach must confirm real seat counts and update them. Confirmed reservations whose start times are less than `RESERVATION_DURATION_MINUTES` (default 90) apart share the same capacity. This is conservative: it may refuse a slot that a table-level model would allow. The venue row is locked during confirmation so concurrent confirmations cannot overbook.

Set `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`. The latter must be at least 16 characters. Admin endpoints expose contact details, so use HTTPS and restrict access to trusted staff.

## Ticketing API

Events, ticket types (batches), and orders that hold stock. Prices are integers in centavos of MZN (`150000` = 1500,00 MZN). No payment is taken: an order stays `PENDING` for `TICKET_HOLD_MINUTES` (default 15), then expires and releases its stock. A payment adapter will call `OrderService.markPaid(reference)`, which is idempotent; there is deliberately no HTTP endpoint that marks an order paid.

Public: `GET /api/events`, `GET /api/events/{slug}` (published events with availability), `POST /api/orders` (`eventSlug`, `fullName`, `phone`, optional `email`, `items: [{ticketTypeId, quantity}]`; `409` when sold out or outside the sale window).

Team only: `GET|POST /api/admin/events`, `PUT /api/admin/events/{id}`, `POST /api/admin/events/{id}/ticket-types`, `PUT /api/admin/ticket-types/{id}`, `GET /api/admin/orders?status=`, `POST /api/admin/orders/{reference}/cancel` (pending orders only). Capacity cannot be lowered below sold + held.

Stock is taken with single conditional `UPDATE` statements, so concurrent buyers cannot oversell (covered by a 40-buyer test for 10 tickets). Expiry cleanup is not covered by an automated test yet.

## Production database

Set `DATABASE_URL` to a PostgreSQL JDBC URL, plus `DB_USERNAME` and `DB_PASSWORD`. Flyway applies versioned schema migrations; Hibernate validates the schema on startup. Configure `CORS_ALLOWED_ORIGINS` to the exact website origin(s). Store credentials in the hosting provider's secret manager, enable HTTPS, backups, monitoring and a rate limit at the edge before accepting public traffic.

Build a container with `docker build -t south-beach-reservations backend/` from the repository root. The container listens on `8080`; pass the production environment variables at runtime.

## Scope

This service stores and manages requests and enforces seat capacity on confirmation, but has no table-level inventory, opening hours or public availability endpoint. It does not yet send confirmations/reminders, or integrate with email/SMS. The team must review a request before confirming it.

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

### WhatsApp

Staff can message customers on WhatsApp from the panel: the *WhatsApp* button on each reservation and on pending or paid ticket orders opens a chat with the customer (`https://wa.me/<number>`) in the staff member's own WhatsApp, with the message already written (reservation received, confirmed or cancelled; ticket reservation with the amount and deadline; the private ticket link once paid). Staff can edit the text before pressing send. Nothing is sent by the server and no WhatsApp account is configured in the system. Numbers are normalised to international format: a leading `00` or `0` is dropped, and a 9-digit number starting with 8 is taken as Mozambican (`+258`); a number that cannot be made international has the button disabled. Messages are in Portuguese. Automatic sending needs the WhatsApp Business Platform (a Meta Business account, a dedicated number and pre-approved message templates) and is not built.

### Staff dashboard

`admin.html` (not linked from the public menu, `noindex`) has three tabs. *Reservas* lists requests, filters by status and confirms or cancels them. *Eventos e bilhetes* creates and edits events (draft, published, cancelled) and their ticket types (price in MZN, capacity, per-order limit, sale window), showing sold, held and available stock; dates are entered in Maputo time. *Encomendas* lists ticket orders and cancels pending ones, which releases their stock. Everything goes through the admin API. Staff sign in with the `APP_ADMIN_*` credentials; they are kept in memory only, so reloading the page signs out. Locally open `http://localhost:8000/admin.html` with the API running.

### Availability

Capacity per venue lives in the `venue_capacity` table. The seeded values (Restaurante 60, Beach Bar 80, Sports Bar 50) are **placeholders**; South Beach must confirm real seat counts and update them. Confirmed reservations whose start times are less than `RESERVATION_DURATION_MINUTES` (default 90) apart share the same capacity. This is conservative: it may refuse a slot that a table-level model would allow. The venue row is locked during confirmation so concurrent confirmations cannot overbook.

Set `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`. The latter must be at least 16 characters. Admin endpoints expose contact details, so use HTTPS and restrict access to trusted staff.

## Ticketing API

Events, ticket types (batches), and orders that hold stock. Prices are integers in centavos of MZN (`150000` = 1500,00 MZN). No payment is taken: an order stays `PENDING` for `TICKET_HOLD_MINUTES` (default 15), then expires and releases its stock. Payment is confirmed in one of two ways: a future payment adapter calls `OrderService.markPaid(reference)`, or staff use `POST /api/admin/orders/{reference}/mark-paid` (the *Marcar como paga* button) after receiving the money by other means. Both are idempotent. There is no public way to mark an order paid.

Public: `GET /api/events`, `GET /api/events/{slug}` (published events with availability), `POST /api/orders` (`eventSlug`, `fullName`, `phone`, optional `email`, `items: [{ticketTypeId, quantity}]`; `409` when sold out or outside the sale window).

Team only: `GET|POST /api/admin/events`, `PUT /api/admin/events/{id}`, `POST /api/admin/events/{id}/ticket-types`, `PUT /api/admin/ticket-types/{id}`, `GET /api/admin/orders?status=`, `POST /api/admin/orders/{reference}/cancel` (pending orders only). Capacity cannot be lowered below sold + held.

Posters: staff upload a JPEG, PNG or WebP of up to 2 MB (`POSTER_MAX_BYTES`) with `PUT /api/admin/events/{id}/poster` (multipart field `file`) and remove it with `DELETE`. The file type is checked from the file's own signature, not the browser's claim. Posters are stored in the database, so they survive hosts with ephemeral disks, and are served publicly only for published events at `GET /api/events/{slug}/poster`; staff can view any poster at `GET /api/admin/events/{id}/poster`.

### QR tickets and entry

Marking an order paid issues one ticket per seat. Each has a random 26-character code (130 bits, no 0/O/1/I) that is the only thing the QR contains; no personal data. The customer's private page is `ticket.html?t=<access token>` (`SITE_URL` sets the site address used in the link); the token is random and unguessable, and only staff see the link in the *Encomendas* tab, where they can copy it. When email or SMS is configured, the link is also sent automatically on payment. `GET /api/tickets/{token}` returns the tickets and `GET /api/tickets/qr/{code}` the QR image.

At the gate, *Entrada* in the staff panel validates a code for the chosen event with `POST /api/admin/check-in` (`{"eventId","code"}`), which always answers 200 with `ADMITTED`, `ALREADY_USED`, `WRONG_EVENT`, `VOID` or `NOT_FOUND`. Admission is a single conditional `UPDATE`, so two scanners reading one code at once admit only one person (tested with 20 concurrent scans). `GET /api/admin/events/{id}/entry-stats` gives admitted vs. issued per ticket type. A wrong-event scan does not consume the ticket. Refunds are covered below.

### Refunds

*Reembolsar* on a paid order (`POST /api/admin/orders/{reference}/refund`, optional `{"note"}`) **records** a refund that staff already paid out themselves (cash, bank transfer, mobile money); the system moves no money. It marks the order `REFUNDED`, voids all its tickets (the gate answers `VOID` and the customer's page shows them as void), returns the seats to sale and keeps the note. It is refused with `409` if the order is not paid or if anyone has already entered with one of its tickets; in that case nothing changes. Repeating a refund changes nothing. Partial refunds and voiding single tickets are not built. Reports no longer count a refunded order as revenue and show the refunded amount separately.

### Sales reports

*Relatórios* in the staff panel shows, per event: revenue, tickets sold, how many people came in, stock held by unpaid orders, a breakdown by ticket type (price, capacity, sold, held, available, revenue, admitted), tickets and revenue per day (Maputo time), and orders per status, plus a table of all events. Only **paid** orders count as revenue. `GET /api/admin/reports/events` and `/events/{id}` return the data; `GET /api/admin/reports/events/{id}/orders.csv` downloads every order (reference, date, status, name, phone, email, tickets, total in MZN) as a UTF-8 CSV that opens in Excel. The CSV contains customers' contact details, so it is staff-only and never cached, and any cell that could run as a spreadsheet formula (starting with `=`, `+`, `-` or `@`) is defused with a leading apostrophe; phone numbers are validated to digits and `+ ( ) . -`, so they keep their leading `+`. Refunded orders are excluded from revenue and shown as a separate amount.

Stock is taken with single conditional `UPDATE` statements, so concurrent buyers cannot oversell (covered by a 40-buyer test for 10 tickets). Expiry cleanup is not covered by an automated test yet.

## Site content (CMS)

Staff edit the website's text, images and links in *Conteúdo do site* in the staff panel, without touching files. Editable elements are marked in the HTML with `data-cms` (text), `data-cms-image` (`<img>`), `data-cms-bg` (background image) and `data-cms-href` (link); `cms.js` fetches `GET /api/content` and applies the edits on top of the page's own content. A page with no edit, or with the API down, shows exactly what is in the HTML. Edits are stored in `site_content` and saved all-or-nothing with `PUT /api/admin/content` (`{"entries": {"key": {"pt","en"}}}`; a null entry restores the original). Phone and email use shared keys (`site.phone`, `site.email`), so one edit updates every page.

Text is plain text: a new line is a line break and `*word*` is italic accent; no HTML is ever interpreted. Links must be `https://`, `tel:`, `mailto:` or a page of this site, and images `https://` or an uploaded file (`POST /api/admin/media`, JPEG/PNG/WebP up to 3 MB, stored in the database and served at `/api/media/{id}`). If staff change the Portuguese but not the English, English visitors see the Portuguese text, and the editor warns about it.

Not editable yet: menus beyond their page text, text inside links with arrows (buttons), navigation and footer, the opening of events/tickets pages, and adding or removing sections. New editable spots are added by marking the element in the HTML.

## Gallery photos

The gallery page shows photos managed in the *Galeria* tab: staff add photos (upload up to 3 MB, or an https address), set a Portuguese and optional English caption, the group (*Espaços* or *Eventos passados*) and the grid format (normal, wide, tall), hide a photo without deleting it, reorder with the arrows and remove it. `GET /api/gallery` is public (visible photos in order); `GET|POST /api/admin/gallery`, `PUT|DELETE /api/admin/gallery/{id}` and `PUT /api/admin/gallery-order` (`{"ids": [...]}`, every photo exactly once) are staff-only. The nine photos that were in `gallery.html` are seeded by migration `V8`, and stay in the HTML as the fallback if the API is down. Removing a photo, or replacing its image, also deletes its uploaded file when no other photo uses it. An English caption left empty falls back to the site's existing translation, then to the Portuguese one.

## Production database

Set `DATABASE_URL` to a PostgreSQL JDBC URL, plus `DB_USERNAME` and `DB_PASSWORD`. Flyway applies versioned schema migrations; Hibernate validates the schema on startup. Configure `CORS_ALLOWED_ORIGINS` to the exact website origin(s). Store credentials in the hosting provider's secret manager, enable HTTPS, backups, monitoring and a rate limit at the edge before accepting public traffic.

Build a container with `docker build -t south-beach-reservations backend/` from the repository root. The container listens on `8080`; pass the production environment variables at runtime.

## Scope

This service stores and manages requests and enforces seat capacity on confirmation, but has no table-level inventory, opening hours or public availability endpoint. It does not yet send confirmations/reminders, or integrate with email/SMS. The team must review a request before confirming it.

## Menus

O separador *Menus* do painel gere os pratos e bebidas de cada espaço (Restaurante, Beach Bar, Sports Bar): secção, nome e descrição em português e inglês, preço em MZN (opcional), ordem e visibilidade. A página `menus.html` lê `GET /api/menu`; a equipa usa `/api/admin/menu` e `PUT /api/admin/menu-order`. Um espaço sem itens continua a mostrar a ligação para o menu completo.

As *divisões* de cada menu (À Lá Carte, Sushi, Tapas, Bebidas, Cocktails…) são geridas no mesmo separador: criam-se por espaço, ordenam-se com as setas (`/api/admin/menu-groups`, `PUT /api/admin/menu-group-order`) e cada item escolhe a sua num menu. Uma divisão só se remove depois de ficar sem itens.

## Eventos passados e importação do site antigo

O separador *Eventos passados* do painel gere o arquivo: título, data (texto livre), horário, local, descrição (PT/EN), capa, ordem, visibilidade e o álbum de cada evento (envio de várias fotografias, ordem, "usar como capa", remoção). Os visitantes veem a lista em `events.html` (`GET /api/past-events`) e cada evento em `past-event.html?e=<endereço>` (`GET /api/past-events/{slug}`).

`backend/tools/import_old_site.py` traz para a API os 12 eventos, 369 fotografias de álbuns e 22 fotografias de espaços do antigo `southbeach.co.mz` (dados em `old-site-events.json`; as imagens são descarregadas, reduzidas a 1600 px e guardadas na base de dados). Pode repetir-se sem duplicar:

    python3 backend/tools/import_old_site.py --api http://localhost:8080 --user admin --password '<APP_ADMIN_PASSWORD>'

Corra-o também contra a API de produção quando esta existir. Os horários de funcionamento (`site.hours`) e as ligações Instagram/Facebook (`site.instagram`, `site.facebook`) editam-se em *Conteúdo do site*.

## Imagens do site

As imagens que vêm com as páginas estão em `assets/img/` (copiadas do site antigo, sem depender dele). Cada uma — logótipo, imagens de fundo e fotografias — pode ser substituída no painel (*Conteúdo do site*, escolha a página); as fotografias da galeria que vieram de origem apontam para esses ficheiros (`assets/img/…` é um endereço aceite pela API). Para publicar, a pasta `assets/` tem de ir junto com as páginas.

## Acessos de porta (validação de bilhetes por quem não é admin)

Quem valida bilhetes à porta não precisa da conta de administrador. No painel, o separador *Acessos de porta* cria contas que **só** podem ler e admitir bilhetes: cada uma recebe um utilizador (`porta-xxxxx`) e um código de 10 caracteres, mostrado uma só vez (guardado só em hash). A pessoa entra em `entrada.html`, que mostra apenas o ecrã de leitura (câmara, código escrito, confirmação manual, últimas entradas e desfazer).

- Pode estar presa a **um evento** e ter **data de fim**; pode ser **pausada**, ter o **código renovado** (o anterior deixa de funcionar) ou ser **removida**, e o efeito é imediato.
- Na API, estas contas só chegam a `/api/gate/**` (eventos abertos, ler `check-in/peek`, confirmar `check-in`, `undo`, contagens e últimas entradas). Tudo o que é `/api/admin/**` responde 403. O administrador também pode usar as rotas `/api/gate/**`.
- A gestão das contas está em `/api/admin/gate-users` (só admin).
- O código é gerado com um alfabeto sem caracteres ambíguos (sem 0/O, 1/I/L); ao escrever, a página tolera minúsculas, espaços e traços.

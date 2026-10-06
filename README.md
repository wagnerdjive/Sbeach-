# South Beach Maputo — website concept

Responsive multi-page website concept for South Beach, Maputo. It takes cues from the editorial storytelling and destination pages on Nikki Beach Marbella, with additional inspiration from SALT's concept-led restaurant story, language selector and food/drinks menu navigation. The design keeps South Beach's own identity, location, services, imagery, and current menu and reservation destinations.

## Run locally

Open `index.html` in a browser, or serve the folder with:

```sh
python3 -m http.server 8000
```

Then visit <http://localhost:8000>.

## Pages and interactions

- `index.html`: editorial homepage with the three venue areas, menu previews, events, gallery and visit details.
- `about.html`: South Beach overview with an editorial concept section about place, food and shared moments.
- `restaurant.html`, `beach-bar.html`, `sports-bar.html`: separate pages for each space.
- `menus.html`: accessible tabs for the three spaces, with food/drinks filters, linking to the current full menus.
- `events.html`: upcoming-events empty state based on the current events page, past-event archive links, and an event inquiry form that prepares an email draft.
- `tickets.html`: interactive bilingual preview of a South Beach owned ticket checkout, with ticket categories, quantities, planned payment methods and a clear prelaunch notice.
- `gallery.html`: filterable photo grid with keyboard-operable lightbox.
- `reservations.html`: bilingual reservation request form connected to the Spring API; stores the request and displays its reference.
- `contact.html`: contact information, published meal hours, directions and map.

## Project notes

- Buildless HTML, CSS and JavaScript; no package install is required.
- A persistent EN/PT switch translates the main page copy, navigation, calls to action, form labels and metadata across pages; the selection is remembered in the browser.
- South Beach's public image assets are loaded from Wix's CDN.
- Menu links continue to the current official South Beach pages, so published prices stay authoritative. Reservation requests are stored by the Spring API and remain pending until the team confirms them.
- The event list currently has no upcoming events. The page displays that state instead of inventing events.
- The ticket selection page is a non-transactional prototype. It does not publish real event prices, accept customer data, contact a payment provider, or issue tickets. See `TICKETING.md` for the backend, security and merchant setup required for real sales.
- The event inquiry form opens a pre-filled email draft; it does not send or store visitor data itself.
- The reservation request form sends and stores requests. It does not check live table availability or send confirmation emails; the team reviews requests in the protected API. See `RESERVATIONS.md` for configuration and remaining production work.
- `backend/`: Java 17 and Spring Boot API for reservation requests, with H2 local storage, PostgreSQL support, protected team endpoints and integration tests.
- Opening times and contact details reflect information currently displayed on the public site and should be reconfirmed before launch.
- This is a multi-page concept in a separate project; it does not replace or publish over the current South Beach site.

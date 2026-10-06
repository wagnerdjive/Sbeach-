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
- `gallery.html`: filterable photo grid with keyboard-operable lightbox.
- `reservations.html`: reservation page linked to the existing official booking flow.
- `contact.html`: contact information, published meal hours, directions and map.

## Project notes

- Buildless HTML, CSS and JavaScript; no package install is required.
- A persistent EN/PT switch translates the main page copy, navigation, calls to action, form labels and metadata across pages; the selection is remembered in the browser.
- South Beach's public image assets are loaded from Wix's CDN.
- Menu links and the reservation action continue to the current official South Beach pages, so published prices and booking availability stay authoritative.
- The event list currently has no upcoming events. The page displays that state instead of inventing events.
- The event inquiry form opens a pre-filled email draft; it does not send or store visitor data itself.
- Opening times and contact details reflect information currently displayed on the public site and should be reconfirmed before launch.
- This is a multi-page concept in a separate project; it does not replace or publish over the current South Beach site.

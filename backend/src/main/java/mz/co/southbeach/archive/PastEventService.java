package mz.co.southbeach.archive;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.content.ImageTypes;
import mz.co.southbeach.content.MediaFileRepository;
import mz.co.southbeach.tickets.service.TicketNotFoundException;
import mz.co.southbeach.tickets.service.TicketRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PastEventService {
    public record EventRequest(
            @NotBlank @Size(max = 120) String titlePt,
            @Size(max = 120) String titleEn,
            @Size(max = 80) String dateTextPt,
            @Size(max = 80) String dateTextEn,
            @Size(max = 60) String timeText,
            @Size(max = 120) String location,
            @Size(max = 2000) String descriptionPt,
            @Size(max = 2000) String descriptionEn,
            @Size(max = 500) String coverUrl,
            Boolean visible) { }

    public record PhotoRequest(@NotBlank @Size(max = 500) String imageUrl) { }

    public record PhotoResponse(Long id, String imageUrl, int position) {
        static PhotoResponse of(PastEventPhoto p) { return new PhotoResponse(p.getId(), p.getImageUrl(), p.getPosition()); }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventResponse(Long id, String slug, String titlePt, String titleEn, String dateTextPt, String dateTextEn,
                                String timeText, String location, String descriptionPt, String descriptionEn, String coverUrl,
                                long photoCount, Integer position, Boolean visible, List<PhotoResponse> photos) { }

    private final PastEventRepository events;
    private final PastEventPhotoRepository photos;
    private final MediaFileRepository media;
    private final Clock clock;

    public PastEventService(PastEventRepository events, PastEventPhotoRepository photos, MediaFileRepository media, Clock clock) {
        this.events = events;
        this.photos = photos;
        this.media = media;
        this.clock = clock;
    }

    private Map<Long, Long> counts() {
        var map = new HashMap<Long, Long>();
        photos.countsByEvent().forEach(row -> map.put((Long) row[0], (Long) row[1]));
        return map;
    }

    private EventResponse of(PastEvent e, long count, boolean admin, List<PastEventPhoto> album) {
        // Without a chosen cover, the first photo of the album stands in for it.
        var cover = admin || e.getCoverUrl() != null ? e.getCoverUrl()
                : album != null ? album.stream().findFirst().map(PastEventPhoto::getImageUrl).orElse(null)
                : photos.findByEventIdOrderByPositionAscIdAsc(e.getId()).stream().findFirst().map(PastEventPhoto::getImageUrl).orElse(null);
        return new EventResponse(e.getId(), e.getSlug(), e.getTitlePt(), e.getTitleEn(), e.getDateTextPt(), e.getDateTextEn(),
                e.getTimeText(), e.getLocation(), e.getDescriptionPt(), e.getDescriptionEn(), cover, count,
                admin ? e.getPosition() : null, admin ? e.isVisible() : null,
                album == null ? null : album.stream().map(PhotoResponse::of).toList());
    }

    @Transactional(readOnly = true)
    public List<EventResponse> published() {
        var counts = counts();
        return events.findByVisibleTrueOrderByPositionAscIdAsc().stream().map(e -> of(e, counts.getOrDefault(e.getId(), 0L), false, null)).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse publishedBySlug(String slug) {
        var event = events.findBySlugAndVisibleTrue(slug).orElseThrow(() -> new TicketNotFoundException("Past event"));
        var album = photos.findByEventIdOrderByPositionAscIdAsc(event.getId());
        return of(event, album.size(), false, album);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> all() {
        var counts = counts();
        return events.findAllByOrderByPositionAscIdAsc().stream().map(e -> of(e, counts.getOrDefault(e.getId(), 0L), true, null)).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse one(Long id) {
        var event = events.findById(id).orElseThrow(() -> new TicketNotFoundException("Past event"));
        var album = photos.findByEventIdOrderByPositionAscIdAsc(id);
        return of(event, album.size(), true, album);
    }

    @Transactional
    public EventResponse add(EventRequest request) {
        validateImage(request.coverUrl());
        var event = new PastEvent(uniqueSlug(request.titlePt()), events.maxPosition() + 1, clock.instant());
        apply(event, request);
        return of(events.save(event), 0, true, List.of());
    }

    @Transactional
    public EventResponse change(Long id, EventRequest request) {
        validateImage(request.coverUrl());
        var event = events.findById(id).orElseThrow(() -> new TicketNotFoundException("Past event"));
        var oldCover = event.getCoverUrl();
        apply(event, request);
        events.flush();
        if (oldCover != null && !oldCover.equals(request.coverUrl())) dropUnusedUpload(oldCover);
        return one(id);
    }

    @Transactional
    public void remove(Long id) {
        var event = events.findById(id).orElseThrow(() -> new TicketNotFoundException("Past event"));
        var urls = new HashSet<>(photos.findByEventIdOrderByPositionAscIdAsc(id).stream().map(PastEventPhoto::getImageUrl).toList());
        if (event.getCoverUrl() != null) urls.add(event.getCoverUrl());
        photos.deleteByEventId(id);
        events.delete(event);
        events.flush();
        urls.forEach(this::dropUnusedUpload);
    }

    /** {@code ids} must be exactly the current events, in the new order. */
    @Transactional
    public List<EventResponse> reorder(List<Long> ids) {
        var current = events.findAll();
        if (ids == null || ids.size() != current.size()
                || !new HashSet<>(ids).equals(new HashSet<>(current.stream().map(PastEvent::getId).toList()))) {
            throw new TicketRequestException("The new order must list every past event exactly once.");
        }
        var byId = new HashMap<Long, PastEvent>();
        current.forEach(e -> byId.put(e.getId(), e));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i + 1);
        return all();
    }

    // ---- Album --------------------------------------------------------------

    @Transactional
    public PhotoResponse addPhoto(Long eventId, PhotoRequest request) {
        if (!events.existsById(eventId)) throw new TicketNotFoundException("Past event");
        validateImage(request.imageUrl());
        return PhotoResponse.of(photos.save(new PastEventPhoto(eventId, request.imageUrl(), photos.maxPosition(eventId) + 1)));
    }

    @Transactional
    public void removePhoto(Long photoId) {
        var photo = photos.findById(photoId).orElseThrow(() -> new TicketNotFoundException("Photo"));
        photos.delete(photo);
        photos.flush();
        dropUnusedUpload(photo.getImageUrl());
    }

    /** {@code ids} must be exactly the photos of that album, in the new order. */
    @Transactional
    public List<PhotoResponse> reorderPhotos(Long eventId, List<Long> ids) {
        var current = photos.findByEventIdOrderByPositionAscIdAsc(eventId);
        if (ids == null || ids.size() != current.size()
                || !new HashSet<>(ids).equals(new HashSet<>(current.stream().map(PastEventPhoto::getId).toList()))) {
            throw new TicketRequestException("The new order must list every photo of the album exactly once.");
        }
        var byId = new HashMap<Long, PastEventPhoto>();
        current.forEach(p -> byId.put(p.getId(), p));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i + 1);
        return photos.findByEventIdOrderByPositionAscIdAsc(eventId).stream().map(PhotoResponse::of).toList();
    }

    // ---- Helpers ------------------------------------------------------------

    private void apply(PastEvent event, EventRequest r) {
        event.update(r.titlePt().strip(), blankToNull(r.titleEn()), blankToNull(r.dateTextPt()), blankToNull(r.dateTextEn()),
                blankToNull(r.timeText()), blankToNull(r.location()), blankToNull(r.descriptionPt()), blankToNull(r.descriptionEn()),
                blankToNull(r.coverUrl()), r.visible() == null || r.visible());
    }

    private void validateImage(String url) {
        if (url == null || url.isBlank()) return;
        if (!ImageTypes.isAllowedUrl(url)) throw new TicketRequestException("The image must be an https address or an uploaded image.");
        if (url.startsWith("/api/media/") && !media.existsById(Long.valueOf(url.substring("/api/media/".length())))) {
            throw new TicketRequestException("That uploaded image does not exist.");
        }
    }

    /** Frees the stored file of a removed or replaced upload when no album photo or cover still shows it. */
    private void dropUnusedUpload(String url) {
        if (url == null || !url.startsWith("/api/media/") || photos.existsByImageUrl(url) || events.existsByCoverUrlAndIdNot(url, -1L)) return;
        media.deleteById(Long.valueOf(url.substring("/api/media/".length())));
    }

    private String uniqueSlug(String title) {
        var base = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "evento";
        if (base.length() > 80) base = base.substring(0, 80).replaceAll("-+$", "");
        var slug = base;
        for (int n = 2; events.existsBySlug(slug); n++) slug = base + "-" + n;
        return slug;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

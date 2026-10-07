package mz.co.southbeach.gallery;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.content.ImageTypes;
import mz.co.southbeach.content.MediaFileRepository;
import mz.co.southbeach.tickets.service.TicketNotFoundException;
import mz.co.southbeach.tickets.service.TicketRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;

@Service
public class GalleryService {
    public record PhotoRequest(
            @NotBlank @Size(max = 500) String imageUrl,
            @NotNull GalleryPhoto.Category category,
            @NotNull GalleryPhoto.Size size,
            @NotBlank @Size(max = 120) String captionPt,
            @Size(max = 120) String captionEn,
            Boolean visible) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PhotoResponse(Long id, String imageUrl, GalleryPhoto.Category category, GalleryPhoto.Size size,
                                String captionPt, String captionEn, Integer position, Boolean visible) {
        static PhotoResponse of(GalleryPhoto p, boolean admin) {
            return new PhotoResponse(p.getId(), p.getImageUrl(), p.getCategory(), p.getSize(), p.getCaptionPt(), p.getCaptionEn(),
                    admin ? p.getPosition() : null, admin ? p.isVisible() : null);
        }
    }

    private final GalleryPhotoRepository photos;
    private final MediaFileRepository media;
    private final Clock clock;

    public GalleryService(GalleryPhotoRepository photos, MediaFileRepository media, Clock clock) {
        this.photos = photos;
        this.media = media;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> published() {
        return photos.findByVisibleTrueOrderByPositionAscIdAsc().stream().map(p -> PhotoResponse.of(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> all() {
        return photos.findAllByOrderByPositionAscIdAsc().stream().map(p -> PhotoResponse.of(p, true)).toList();
    }

    @Transactional
    public PhotoResponse add(PhotoRequest request) {
        validate(request);
        var photo = photos.save(new GalleryPhoto(request.imageUrl(), request.category(), request.size(), request.captionPt().strip(),
                blankToNull(request.captionEn()), photos.maxPosition() + 1, request.visible() == null || request.visible(), clock.instant()));
        return PhotoResponse.of(photo, true);
    }

    @Transactional
    public PhotoResponse change(Long id, PhotoRequest request) {
        validate(request);
        var photo = photos.findById(id).orElseThrow(() -> new TicketNotFoundException("Photo"));
        var oldUrl = photo.getImageUrl();
        photo.update(request.imageUrl(), request.category(), request.size(), request.captionPt().strip(),
                blankToNull(request.captionEn()), request.visible() == null || request.visible());
        photos.flush();
        if (!oldUrl.equals(request.imageUrl())) dropUnusedUpload(oldUrl, id);
        return PhotoResponse.of(photo, true);
    }

    @Transactional
    public void remove(Long id) {
        var photo = photos.findById(id).orElseThrow(() -> new TicketNotFoundException("Photo"));
        photos.delete(photo);
        photos.flush();
        dropUnusedUpload(photo.getImageUrl(), id);
    }

    /** {@code ids} must be exactly the current photos, in the new order. */
    @Transactional
    public List<PhotoResponse> reorder(List<Long> ids) {
        var current = photos.findAll();
        if (ids == null || ids.size() != current.size() || !new HashSet<>(ids).equals(
                new HashSet<>(current.stream().map(GalleryPhoto::getId).toList()))) {
            throw new TicketRequestException("The new order must list every photo exactly once.");
        }
        var byId = new java.util.HashMap<Long, GalleryPhoto>();
        current.forEach(p -> byId.put(p.getId(), p));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i + 1);
        return all();
    }

    private void validate(PhotoRequest request) {
        if (!ImageTypes.isAllowedUrl(request.imageUrl())) {
            throw new TicketRequestException("The photo must be an https address or an uploaded image.");
        }
        if (request.imageUrl().startsWith("/api/media/")
                && !media.existsById(Long.valueOf(request.imageUrl().substring("/api/media/".length())))) {
            throw new TicketRequestException("That uploaded image does not exist.");
        }
    }

    /** Frees the stored file of a replaced or removed upload when no other photo still shows it. */
    private void dropUnusedUpload(String url, Long exceptPhotoId) {
        if (!url.startsWith("/api/media/") || photos.existsByImageUrlAndIdNot(url, exceptPhotoId)) return;
        media.deleteById(Long.valueOf(url.substring("/api/media/".length())));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

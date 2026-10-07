package mz.co.southbeach.menu;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.tickets.service.TicketNotFoundException;
import mz.co.southbeach.tickets.service.TicketRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Service
public class MenuService {
    public record ItemRequest(
            @NotNull MenuItem.Venue venue,
            @NotNull MenuItem.Kind kind,
            @NotBlank @Size(max = 80) String sectionPt,
            @Size(max = 80) String sectionEn,
            @NotBlank @Size(max = 120) String namePt,
            @Size(max = 120) String nameEn,
            @Size(max = 400) String descriptionPt,
            @Size(max = 400) String descriptionEn,
            @Min(0) Long priceCents,
            Boolean visible) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResponse(Long id, MenuItem.Venue venue, MenuItem.Kind kind, String sectionPt, String sectionEn,
                               String namePt, String nameEn, String descriptionPt, String descriptionEn,
                               Long priceCents, Integer position, Boolean visible) {
        static ItemResponse of(MenuItem i, boolean admin) {
            return new ItemResponse(i.getId(), i.getVenue(), i.getKind(), i.getSectionPt(), i.getSectionEn(), i.getNamePt(),
                    i.getNameEn(), i.getDescriptionPt(), i.getDescriptionEn(), i.getPriceCents(),
                    admin ? i.getPosition() : null, admin ? i.isVisible() : null);
        }
    }

    private final MenuItemRepository items;
    private final Clock clock;

    public MenuService(MenuItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> published() {
        return items.findByVisibleTrueOrderByPositionAscIdAsc().stream().map(i -> ItemResponse.of(i, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> all() {
        return items.findAllByOrderByPositionAscIdAsc().stream().map(i -> ItemResponse.of(i, true)).toList();
    }

    @Transactional
    public ItemResponse add(ItemRequest request) {
        var item = new MenuItem(items.maxPosition() + 1, clock.instant());
        apply(item, request);
        return ItemResponse.of(items.save(item), true);
    }

    @Transactional
    public ItemResponse change(Long id, ItemRequest request) {
        var item = items.findById(id).orElseThrow(() -> new TicketNotFoundException("Menu item"));
        apply(item, request);
        return ItemResponse.of(item, true);
    }

    @Transactional
    public void remove(Long id) {
        items.delete(items.findById(id).orElseThrow(() -> new TicketNotFoundException("Menu item")));
    }

    /** {@code ids} must be exactly the current items, in the new order. */
    @Transactional
    public List<ItemResponse> reorder(List<Long> ids) {
        var current = items.findAll();
        if (ids == null || ids.size() != current.size()
                || !new HashSet<>(ids).equals(new HashSet<>(current.stream().map(MenuItem::getId).toList()))) {
            throw new TicketRequestException("The new order must list every menu item exactly once.");
        }
        var byId = new HashMap<Long, MenuItem>();
        current.forEach(i -> byId.put(i.getId(), i));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i + 1);
        return all();
    }

    private void apply(MenuItem item, ItemRequest r) {
        item.update(r.venue(), r.kind(), r.sectionPt().strip(), blankToNull(r.sectionEn()), r.namePt().strip(),
                blankToNull(r.nameEn()), blankToNull(r.descriptionPt()), blankToNull(r.descriptionEn()), r.priceCents(),
                r.visible() == null || r.visible());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

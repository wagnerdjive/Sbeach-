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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class MenuService {
    public record ItemRequest(
            @NotNull MenuItem.Venue venue,
            @NotNull MenuItem.Kind kind,
            Long groupId,
            @NotBlank @Size(max = 80) String sectionPt,
            @Size(max = 80) String sectionEn,
            @NotBlank @Size(max = 120) String namePt,
            @Size(max = 120) String nameEn,
            @Size(max = 400) String descriptionPt,
            @Size(max = 400) String descriptionEn,
            @Min(0) Long priceCents,
            Boolean visible) { }

    public record GroupRequest(
            @NotNull MenuItem.Venue venue,
            @NotBlank @Size(max = 60) String namePt,
            @Size(max = 60) String nameEn) { }

    public record GroupResponse(Long id, MenuItem.Venue venue, String namePt, String nameEn, int position) {
        static GroupResponse of(MenuGroup g) { return new GroupResponse(g.getId(), g.getVenue(), g.getNamePt(), g.getNameEn(), g.getPosition()); }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResponse(Long id, MenuItem.Venue venue, MenuItem.Kind kind, Long groupId, String groupPt, String groupEn,
                               String sectionPt, String sectionEn, String namePt, String nameEn, String descriptionPt,
                               String descriptionEn, Long priceCents, Integer position, Boolean visible) {
        static ItemResponse of(MenuItem i, MenuGroup g, boolean admin) {
            return new ItemResponse(i.getId(), i.getVenue(), i.getKind(), i.getGroupId(), g == null ? null : g.getNamePt(),
                    g == null ? null : g.getNameEn(), i.getSectionPt(), i.getSectionEn(), i.getNamePt(), i.getNameEn(),
                    i.getDescriptionPt(), i.getDescriptionEn(), i.getPriceCents(), admin ? i.getPosition() : null,
                    admin ? i.isVisible() : null);
        }
    }

    private final MenuItemRepository items;
    private final MenuGroupRepository groups;
    private final Clock clock;

    public MenuService(MenuItemRepository items, MenuGroupRepository groups, Clock clock) {
        this.items = items;
        this.groups = groups;
        this.clock = clock;
    }

    private Map<Long, MenuGroup> groupsById() {
        var map = new HashMap<Long, MenuGroup>();
        groups.findAll().forEach(g -> map.put(g.getId(), g));
        return map;
    }

    /** Visitors get the items by division order (as staff arranged it), then item order. Items without a division come last. */
    @Transactional(readOnly = true)
    public List<ItemResponse> published() {
        var byId = groupsById();
        return items.findByVisibleTrueOrderByPositionAscIdAsc().stream()
                .sorted(Comparator.comparingInt((MenuItem i) -> i.getGroupId() == null ? Integer.MAX_VALUE : byId.get(i.getGroupId()).getPosition()))
                .map(i -> ItemResponse.of(i, byId.get(i.getGroupId()), false)).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> all() {
        var byId = groupsById();
        return items.findAllByOrderByPositionAscIdAsc().stream().map(i -> ItemResponse.of(i, byId.get(i.getGroupId()), true)).toList();
    }

    @Transactional
    public ItemResponse add(ItemRequest request) {
        var item = new MenuItem(items.maxPosition() + 1, clock.instant());
        apply(item, request);
        return one(items.save(item));
    }

    @Transactional
    public ItemResponse change(Long id, ItemRequest request) {
        var item = items.findById(id).orElseThrow(() -> new TicketNotFoundException("Menu item"));
        apply(item, request);
        return one(item);
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

    // ---- Divisions ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GroupResponse> allGroups() {
        return groups.findAllByOrderByVenueAscPositionAscIdAsc().stream().map(GroupResponse::of).toList();
    }

    @Transactional
    public GroupResponse addGroup(GroupRequest request) {
        ensureNameFree(request.venue(), request.namePt(), -1L);
        return GroupResponse.of(groups.save(new MenuGroup(request.venue(), request.namePt().strip(),
                blankToNull(request.nameEn()), groups.maxPosition(request.venue()) + 1)));
    }

    /** Renames a division; the space it belongs to cannot change. */
    @Transactional
    public GroupResponse changeGroup(Long id, GroupRequest request) {
        var group = groups.findById(id).orElseThrow(() -> new TicketNotFoundException("Menu division"));
        ensureNameFree(group.getVenue(), request.namePt(), id);
        group.rename(request.namePt().strip(), blankToNull(request.nameEn()));
        return GroupResponse.of(group);
    }

    @Transactional
    public void removeGroup(Long id) {
        var group = groups.findById(id).orElseThrow(() -> new TicketNotFoundException("Menu division"));
        if (items.existsByGroupId(id)) {
            throw new TicketRequestException("This division still has items. Move or remove them first.");
        }
        groups.delete(group);
    }

    /** {@code ids} must be exactly the divisions of that space, in the new order. */
    @Transactional
    public List<GroupResponse> reorderGroups(MenuItem.Venue venue, List<Long> ids) {
        var current = groups.findByVenueOrderByPositionAscIdAsc(venue);
        if (ids == null || ids.size() != current.size()
                || !new HashSet<>(ids).equals(new HashSet<>(current.stream().map(MenuGroup::getId).toList()))) {
            throw new TicketRequestException("The new order must list every division of this space exactly once.");
        }
        var byId = new HashMap<Long, MenuGroup>();
        current.forEach(g -> byId.put(g.getId(), g));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i + 1);
        return allGroups();
    }

    private void ensureNameFree(MenuItem.Venue venue, String namePt, Long exceptId) {
        if (groups.existsByVenueAndNamePtIgnoreCaseAndIdNot(venue, namePt.strip(), exceptId)) {
            throw new TicketRequestException("This space already has a division with that name.");
        }
    }

    private ItemResponse one(MenuItem item) {
        return ItemResponse.of(item, item.getGroupId() == null ? null : groups.findById(item.getGroupId()).orElse(null), true);
    }

    private void apply(MenuItem item, ItemRequest r) {
        if (r.groupId() != null) {
            var group = groups.findById(r.groupId()).orElseThrow(() -> new TicketRequestException("That division does not exist."));
            if (group.getVenue() != r.venue()) throw new TicketRequestException("That division belongs to another space.");
        }
        item.update(r.venue(), r.kind(), r.groupId(), r.sectionPt().strip(), blankToNull(r.sectionEn()), r.namePt().strip(),
                blankToNull(r.nameEn()), blankToNull(r.descriptionPt()), blankToNull(r.descriptionEn()), r.priceCents(),
                r.visible() == null || r.visible());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

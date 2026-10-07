package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.domain.Event;
import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketOrderItem;
import mz.co.southbeach.tickets.domain.TicketType;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.repository.IssuedTicketRepository;
import mz.co.southbeach.tickets.repository.TicketOrderItemRepository;
import mz.co.southbeach.tickets.repository.TicketOrderRepository;
import mz.co.southbeach.tickets.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Sales figures for staff. Only paid orders count as revenue; pending ones are shown separately. */
@Service
public class ReportService {
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    public record TypeRow(Long ticketTypeId, String name, long priceMinor, int capacity, int sold, int held, int available,
                          long revenueMinor, long admitted) { }
    public record DayRow(LocalDate date, long tickets, long revenueMinor) { }
    public record StatusRow(OrderStatus status, long orders, long tickets) { }
    public record SalesReport(Long eventId, String title, Instant startsAt, long paidOrders, long ticketsSold, long revenueMinor,
                              long refundedMinor, long admitted, List<TypeRow> byType, List<DayRow> byDay, List<StatusRow> byStatus) { }

    private final EventRepository events;
    private final TicketTypeRepository types;
    private final TicketOrderItemRepository items;
    private final TicketOrderRepository orders;
    private final IssuedTicketRepository issued;

    public ReportService(EventRepository events, TicketTypeRepository types, TicketOrderItemRepository items,
                         TicketOrderRepository orders, IssuedTicketRepository issued) {
        this.events = events;
        this.types = types;
        this.items = items;
        this.orders = orders;
        this.issued = issued;
    }

    @Transactional(readOnly = true)
    public List<SalesReport> allEvents() {
        return events.findAllByOrderByStartsAtDesc().stream().map(this::build).toList();
    }

    @Transactional(readOnly = true)
    public SalesReport forEvent(Long eventId) {
        return build(events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event")));
    }

    private SalesReport build(Event event) {
        var eventTypes = types.findByEventIdOrderByIdAsc(event.getId());
        if (eventTypes.isEmpty()) {
            return new SalesReport(event.getId(), event.getTitle(), event.getStartsAt(), 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }
        var typeIds = eventTypes.stream().map(TicketType::getId).toList();

        var revenueByType = new HashMap<Long, Long>();
        var paidOrderIds = new HashSet<Long>();
        var perDay = new TreeMap<LocalDate, long[]>(); // [tickets, revenue]
        for (var row : items.paidLines(typeIds)) {
            long typeId = (Long) row[0], orderId = (Long) row[1], quantity = ((Number) row[3]).longValue(), price = (Long) row[4];
            long revenue = quantity * price;
            revenueByType.merge(typeId, revenue, Long::sum);
            paidOrderIds.add(orderId);
            var day = perDay.computeIfAbsent(((Instant) row[2]).atZone(MAPUTO).toLocalDate(), d -> new long[2]);
            day[0] += quantity;
            day[1] += revenue;
        }

        var admittedByType = new HashMap<Long, Long>();
        issued.countByType(event.getId()).forEach(r -> admittedByType.put((Long) r[0], ((Number) r[2]).longValue()));

        var byType = eventTypes.stream().map(t -> new TypeRow(t.getId(), t.getName(), t.getPriceMinor(), t.getCapacity(),
                t.getSold(), t.getHeld(), t.available(), revenueByType.getOrDefault(t.getId(), 0L), admittedByType.getOrDefault(t.getId(), 0L))).toList();
        var byDay = perDay.entrySet().stream().map(e -> new DayRow(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        var byStatus = items.countByStatus(typeIds).stream()
                .map(r -> new StatusRow((OrderStatus) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .sorted(Comparator.comparing(StatusRow::status)).toList();

        return new SalesReport(event.getId(), event.getTitle(), event.getStartsAt(), paidOrderIds.size(),
                byType.stream().mapToLong(t -> t.sold()).sum(), byType.stream().mapToLong(TypeRow::revenueMinor).sum(),
                items.refundedValue(typeIds), byType.stream().mapToLong(TypeRow::admitted).sum(), byType, byDay, byStatus);
    }

    /** All orders of an event as CSV (Excel-friendly, UTF-8 with BOM). Cells that a spreadsheet could run as a formula are neutralised. */
    @Transactional(readOnly = true)
    public String ordersCsv(Long eventId) {
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var eventTypes = types.findByEventIdOrderByIdAsc(eventId);
        var names = eventTypes.stream().collect(Collectors.toMap(TicketType::getId, TicketType::getName));
        var typeIds = eventTypes.stream().map(TicketType::getId).toList();
        var out = new StringBuilder("﻿");
        out.append(csv("Referência", "Criada em (Maputo)", "Estado", "Nome", "Telefone", "Email", "Bilhetes", "Total (MZN)")).append("\r\n");
        if (typeIds.isEmpty()) return out.toString();
        Map<Long, List<TicketOrderItem>> linesByOrder = items.findByTicketTypeIdIn(typeIds).stream()
                .collect(Collectors.groupingBy(TicketOrderItem::getOrderId));
        var format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(MAPUTO);
        for (var order : orders.findForTypes(typeIds)) {
            var lines = linesByOrder.getOrDefault(order.getId(), List.of()).stream()
                    .map(l -> l.getQuantity() + " × " + names.get(l.getTicketTypeId())).collect(Collectors.joining("; "));
            out.append(csv(order.getReference(), format.format(order.getCreatedAt()), order.getStatus().name(), order.getFullName()))
                    .append(',').append(phoneCell(order.getPhone())).append(',')
                    .append(csv(order.getEmail() == null ? "" : order.getEmail(), lines,
                            BigDecimal.valueOf(order.getTotalMinor(), 2).toPlainString())).append("\r\n");
        }
        return out.toString();
    }

    static String csv(String... cells) {
        return Arrays.stream(cells).map(ReportService::cell).collect(Collectors.joining(","));
    }

    /** Phone numbers are validated to digits and + ( ) . - only, so they cannot carry a formula and keep their leading +. */
    private static String phoneCell(String phone) {
        return "\"" + phone.replace("\"", "\"\"") + "\"";
    }

    private static String cell(String value) {
        var text = value == null ? "" : value;
        // A leading = + - @ tab or CR can make Excel/Sheets execute the cell as a formula.
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}

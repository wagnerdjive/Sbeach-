package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.TicketOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TicketOrderItemRepository extends JpaRepository<TicketOrderItem, Long> {
    List<TicketOrderItem> findByOrderId(Long orderId);
    List<TicketOrderItem> findByTicketTypeIdIn(Collection<Long> typeIds);

    /** Rows of [ticketTypeId, orderId, orderUpdatedAt, quantity, unitPriceMinor] for paid orders; a paid order only changes when refunded, so updatedAt is when it was paid. */
    @Query("select i.ticketTypeId, o.id, o.updatedAt, i.quantity, i.unitPriceMinor from TicketOrderItem i, TicketOrder o "
            + "where i.orderId = o.id and o.status = mz.co.southbeach.tickets.domain.OrderStatus.PAID and i.ticketTypeId in :typeIds")
    List<Object[]> paidLines(Collection<Long> typeIds);

    /** Total value in centavos of refunded orders. */
    @Query("select coalesce(sum(i.quantity * i.unitPriceMinor), 0) from TicketOrderItem i, TicketOrder o "
            + "where i.orderId = o.id and o.status = mz.co.southbeach.tickets.domain.OrderStatus.REFUNDED and i.ticketTypeId in :typeIds")
    long refundedValue(Collection<Long> typeIds);

    /** Rows of [status, orders, tickets]. */
    @Query("select o.status, count(distinct o.id), sum(i.quantity) from TicketOrderItem i, TicketOrder o "
            + "where i.orderId = o.id and i.ticketTypeId in :typeIds group by o.status")
    List<Object[]> countByStatus(Collection<Long> typeIds);
}

package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.TicketOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketOrderItemRepository extends JpaRepository<TicketOrderItem, Long> {
    List<TicketOrderItem> findByOrderId(Long orderId);
}

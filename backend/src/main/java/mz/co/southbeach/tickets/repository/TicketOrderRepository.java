package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {
    Optional<TicketOrder> findByReference(String reference);
    Page<TicketOrder> findByStatus(OrderStatus status, Pageable pageable);
    List<TicketOrder> findByStatusAndExpiresAtBefore(OrderStatus status, Instant instant);
}

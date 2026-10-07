package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.IssuedTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IssuedTicketRepository extends JpaRepository<IssuedTicket, Long> {
    List<IssuedTicket> findByOrderIdOrderByIdAsc(Long orderId);
    Optional<IssuedTicket> findByCode(String code);
    boolean existsByOrderId(Long orderId);

    /**
     * Marks a still-valid ticket as used in one conditional UPDATE, so two scanners reading the same
     * code at once can never both admit it: exactly one call returns 1.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update IssuedTicket t set t.status = mz.co.southbeach.tickets.domain.TicketStatus.USED, t.usedAt = :now "
            + "where t.id = :id and t.status = mz.co.southbeach.tickets.domain.TicketStatus.VALID")
    int markUsed(Long id, Instant now);

    /** Takes back an admission recorded by mistake: only a ticket that is currently used goes back to valid. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update IssuedTicket t set t.status = mz.co.southbeach.tickets.domain.TicketStatus.VALID, t.usedAt = null "
            + "where t.id = :id and t.status = mz.co.southbeach.tickets.domain.TicketStatus.USED")
    int markValidAgain(Long id);

    java.util.List<IssuedTicket> findTop15ByEventIdAndStatusOrderByUsedAtDesc(Long eventId, mz.co.southbeach.tickets.domain.TicketStatus status);

    /** Voids every still-valid ticket of an order; tickets already used at the gate are left as they are. Returns how many were voided. */
    @Modifying(flushAutomatically = true)
    @Query("update IssuedTicket t set t.status = mz.co.southbeach.tickets.domain.TicketStatus.VOID "
            + "where t.orderId = :orderId and t.status = mz.co.southbeach.tickets.domain.TicketStatus.VALID")
    int voidValidOfOrder(Long orderId);

    long countByOrderIdAndStatus(Long orderId, mz.co.southbeach.tickets.domain.TicketStatus status);

    /** Rows of [ticketTypeId, issued, used]. */
    @Query("select t.ticketTypeId, count(t), sum(case when t.status = mz.co.southbeach.tickets.domain.TicketStatus.USED then 1 else 0 end) "
            + "from IssuedTicket t where t.eventId = :eventId and t.status <> mz.co.southbeach.tickets.domain.TicketStatus.VOID group by t.ticketTypeId")
    List<Object[]> countByType(Long eventId);
}

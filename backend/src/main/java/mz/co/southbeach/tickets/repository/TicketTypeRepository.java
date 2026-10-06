package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Stock changes are single conditional UPDATE statements, so concurrent buyers cannot oversell:
 * the database re-checks availability atomically and reports whether the change was applied.
 * The persistence context is not cleared, so orders loaded in the same transaction stay managed.
 */
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    List<TicketType> findByEventIdOrderByIdAsc(Long eventId);
    List<TicketType> findByEventIdInOrderByIdAsc(List<Long> eventIds);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            update TicketType t set t.held = t.held + :quantity
            where t.id = :id and t.capacity - t.sold - t.held >= :quantity
              and (t.saleStartsAt is null or t.saleStartsAt <= :now)
              and (t.saleEndsAt is null or t.saleEndsAt > :now)
            """)
    int hold(Long id, int quantity, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update TicketType t set t.held = t.held - :quantity where t.id = :id and t.held >= :quantity")
    int release(Long id, int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update TicketType t set t.held = t.held - :quantity, t.sold = t.sold + :quantity where t.id = :id and t.held >= :quantity")
    int sell(Long id, int quantity);

    /** Lowering capacity below what is sold or held is refused (0 rows). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TicketType t set t.capacity = :capacity where t.id = :id and t.sold + t.held <= :capacity")
    int changeCapacity(Long id, int capacity);
}

package mz.co.southbeach.archive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PastEventRepository extends JpaRepository<PastEvent, Long> {
    List<PastEvent> findAllByOrderByPositionAscIdAsc();
    List<PastEvent> findByVisibleTrueOrderByPositionAscIdAsc();
    Optional<PastEvent> findBySlugAndVisibleTrue(String slug);
    boolean existsBySlug(String slug);
    boolean existsByCoverUrlAndIdNot(String coverUrl, Long id);

    @Query("select coalesce(max(e.position), 0) from PastEvent e")
    int maxPosition();
}

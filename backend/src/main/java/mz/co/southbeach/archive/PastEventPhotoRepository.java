package mz.co.southbeach.archive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PastEventPhotoRepository extends JpaRepository<PastEventPhoto, Long> {
    List<PastEventPhoto> findByEventIdOrderByPositionAscIdAsc(Long eventId);
    long countByEventId(Long eventId);
    void deleteByEventId(Long eventId);
    boolean existsByImageUrlAndIdNot(String imageUrl, Long id);
    boolean existsByImageUrl(String imageUrl);

    @Query("select coalesce(max(p.position), 0) from PastEventPhoto p where p.eventId = :eventId")
    int maxPosition(Long eventId);

    @Query("select p.eventId, count(p) from PastEventPhoto p group by p.eventId")
    List<Object[]> countsByEvent();
}

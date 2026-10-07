package mz.co.southbeach.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MenuGroupRepository extends JpaRepository<MenuGroup, Long> {
    List<MenuGroup> findAllByOrderByVenueAscPositionAscIdAsc();
    List<MenuGroup> findByVenueOrderByPositionAscIdAsc(MenuItem.Venue venue);
    boolean existsByVenueAndNamePtIgnoreCaseAndIdNot(MenuItem.Venue venue, String namePt, Long id);

    @Query("select coalesce(max(g.position), 0) from MenuGroup g where g.venue = :venue")
    int maxPosition(MenuItem.Venue venue);
}

package mz.co.southbeach.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findAllByOrderByPositionAscIdAsc();
    List<MenuItem> findByVisibleTrueOrderByPositionAscIdAsc();

    boolean existsByGroupId(Long groupId);
    java.util.List<MenuItem> findByGroupId(Long groupId);

    @Query("select coalesce(max(i.position), 0) from MenuItem i")
    int maxPosition();
}

package mz.co.southbeach.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, Long> {
    List<GalleryPhoto> findAllByOrderByPositionAscIdAsc();
    List<GalleryPhoto> findByVisibleTrueOrderByPositionAscIdAsc();
    boolean existsByImageUrlAndIdNot(String imageUrl, Long id);

    @Query("select coalesce(max(p.position), 0) from GalleryPhoto p")
    int maxPosition();
}

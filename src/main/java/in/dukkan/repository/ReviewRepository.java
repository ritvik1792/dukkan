package in.dukkan.repository;

import in.dukkan.domain.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, String> {
    List<Review> findByCatalogProductIdOrderByCreatedAtDesc(String catalogProductId);

    List<Review> findByShopIdOrderByCreatedAtDesc(String shopId);

    List<Review> findAllByOrderByCreatedAtDesc();
}

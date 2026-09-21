package in.dukkan.repository;

import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.ProductRequestStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRequestRepository extends JpaRepository<ProductRequest, String> {
    List<ProductRequest> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<ProductRequest> findByStatusInAndExpiresAtBefore(
            Collection<ProductRequestStatus> statuses, Instant before);
}

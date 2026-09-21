package in.dukkan.repository;

import in.dukkan.domain.Offer;
import in.dukkan.domain.OfferStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, String> {
    List<Offer> findByRequestIdOrderByCreatedAtAsc(String requestId);

    List<Offer> findByRequestIdAndStatus(String requestId, OfferStatus status);

    List<Offer> findByStatusInAndExpiresAtBefore(Collection<OfferStatus> statuses, Instant before);

    long countByRequestIdAndStatus(String requestId, OfferStatus status);
}

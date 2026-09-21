package in.dukkan.repository;

import in.dukkan.domain.RequestShop;
import in.dukkan.domain.RequestShopStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestShopRepository extends JpaRepository<RequestShop, String> {
    List<RequestShop> findByRequestIdOrderByRankScoreDesc(String requestId);

    List<RequestShop> findByRequestIdAndStatus(String requestId, RequestShopStatus status);

    List<RequestShop> findByShopIdInAndStatusInOrderByNotifiedAtDesc(
            Collection<String> shopIds, Collection<RequestShopStatus> statuses);

    Optional<RequestShop> findByRequestIdAndShopId(String requestId, String shopId);

    List<RequestShop> findByShopId(String shopId);

    long countByShopIdAndStatus(String shopId, RequestShopStatus status);
}

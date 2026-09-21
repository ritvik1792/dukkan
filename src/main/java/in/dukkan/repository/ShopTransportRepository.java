package in.dukkan.repository;

import in.dukkan.domain.ShopTransport;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopTransportRepository extends JpaRepository<ShopTransport, String> {
    List<ShopTransport> findByShopIdOrderByLabelAsc(String shopId);

    List<ShopTransport> findByShopIdInOrderByLabelAsc(Collection<String> shopIds);
}

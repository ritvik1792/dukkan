package in.dukkan.repository;

import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, String> {
    List<Shop> findByStatus(ShopStatus status);

    List<Shop> findByOwnerUserId(String ownerUserId);
}

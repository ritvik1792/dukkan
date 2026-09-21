package in.dukkan.repository;

import in.dukkan.domain.ShopPartner;
import in.dukkan.domain.ShopPartnerId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopPartnerRepository extends JpaRepository<ShopPartner, ShopPartnerId> {
    List<ShopPartner> findByShopId(String shopId);

    long countByShopId(String shopId);

    boolean existsByShopIdAndPartnerId(String shopId, String partnerId);

    void deleteByShopIdAndPartnerId(String shopId, String partnerId);
}

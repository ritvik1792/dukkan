package in.dukkan.repository;

import in.dukkan.domain.Partner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends JpaRepository<Partner, String> {

    @Query("""
            SELECT p FROM Partner p
            WHERE p.id IN (SELECT sp.partnerId FROM ShopPartner sp WHERE sp.shopId = :shopId)
            ORDER BY p.name
            """)
    List<Partner> findByShopId(@Param("shopId") String shopId);

    @Query("""
            SELECT DISTINCT p FROM Partner p
            WHERE p.id IN (SELECT sp.partnerId FROM ShopPartner sp)
            ORDER BY p.name
            """)
    List<Partner> findAssignedToAnyShop();

    @Query("""
            SELECT p FROM Partner p
            WHERE p.id NOT IN (SELECT sp.partnerId FROM ShopPartner sp WHERE sp.shopId = :shopId)
            ORDER BY p.name
            """)
    List<Partner> findUnassignedToShop(@Param("shopId") String shopId);
}

package in.dukkan.repository;

import in.dukkan.domain.ProviderType;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopRepository extends JpaRepository<Shop, String> {
    List<Shop> findByStatus(ShopStatus status);

    List<Shop> findByOwnerUserId(String ownerUserId);

    List<Shop> findByStatusAndServicesAllowedTrue(ShopStatus status);

    List<Shop> findByStatusAndProviderType(ShopStatus status, ProviderType providerType);

    @Query("""
            SELECT s FROM Shop s
            WHERE s.status = in.dukkan.domain.ShopStatus.ACTIVE
              AND (
                LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.profession, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.serviceArea, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    List<Shop> searchActive(@Param("q") String q);
}

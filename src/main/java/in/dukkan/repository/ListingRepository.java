package in.dukkan.repository;

import in.dukkan.domain.Listing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, String> {
    List<Listing> findByShopId(String shopId);

    List<Listing> findByCatalogProductId(String catalogProductId);
}

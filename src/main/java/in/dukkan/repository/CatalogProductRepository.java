package in.dukkan.repository;

import in.dukkan.domain.CatalogProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, String> {

    @Query("""
            SELECT p FROM CatalogProduct p
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    List<CatalogProduct> search(@Param("q") String q);
}

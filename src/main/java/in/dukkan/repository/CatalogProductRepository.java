package in.dukkan.repository;

import in.dukkan.domain.CatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, String> {}

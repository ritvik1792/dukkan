package in.dukkan.repository;

import in.dukkan.domain.ShopEmployee;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopEmployeeRepository extends JpaRepository<ShopEmployee, String> {
    List<ShopEmployee> findByShopIdOrderByNameAsc(String shopId);

    List<ShopEmployee> findByShopIdInOrderByNameAsc(Collection<String> shopIds);
}

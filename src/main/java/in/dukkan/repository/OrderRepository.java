package in.dukkan.repository;

import in.dukkan.domain.CustomerOrder;
import in.dukkan.domain.OrderStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, String> {
    List<CustomerOrder> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<CustomerOrder> findByShopIdOrderByCreatedAtDesc(String shopId);

    List<CustomerOrder> findByShopIdAndStatusInOrderByCreatedAtDesc(
            String shopId, Collection<OrderStatus> statuses);

    List<CustomerOrder> findAllByOrderByCreatedAtDesc();
}

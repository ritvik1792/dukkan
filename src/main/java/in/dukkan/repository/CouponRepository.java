package in.dukkan.repository;

import in.dukkan.domain.Coupon;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, String> {
    List<Coupon> findByShopId(String shopId);

    List<Coupon> findAllByOrderByIdDesc();
}

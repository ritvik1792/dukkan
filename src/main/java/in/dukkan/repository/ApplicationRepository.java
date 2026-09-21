package in.dukkan.repository;

import in.dukkan.domain.SellerApplication;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<SellerApplication, String> {
    List<SellerApplication> findByUserIdOrderBySubmittedAtDesc(String userId);

    List<SellerApplication> findAllByOrderBySubmittedAtDesc();
}

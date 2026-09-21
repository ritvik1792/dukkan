package in.dukkan.repository;

import in.dukkan.domain.Advertisement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertisementRepository extends JpaRepository<Advertisement, String> {
    List<Advertisement> findByActiveTrue();
}

package in.dukkan.repository;

import in.dukkan.domain.ProviderService;
import in.dukkan.domain.ServiceStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderServiceRepository extends JpaRepository<ProviderService, String> {
    List<ProviderService> findByProviderIdOrderByCreatedAtDesc(String providerId);

    List<ProviderService> findByProviderIdAndStatus(String providerId, ServiceStatus status);

    List<ProviderService> findByStatus(ServiceStatus status);

    @Query("""
            SELECT s FROM ProviderService s
            WHERE s.status = in.dukkan.domain.ServiceStatus.ACTIVE
              AND (
                LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.serviceArea, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    List<ProviderService> searchActive(@Param("q") String q);

    List<ProviderService> findByProviderIdInAndStatus(Collection<String> providerIds, ServiceStatus status);
}

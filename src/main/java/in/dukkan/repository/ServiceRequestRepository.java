package in.dukkan.repository;

import in.dukkan.domain.ServiceRequest;
import in.dukkan.domain.ServiceRequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, String> {
    List<ServiceRequest> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<ServiceRequest> findByProviderIdOrderByCreatedAtDesc(String providerId);

    List<ServiceRequest> findByProviderIdAndStatusOrderByCreatedAtDesc(
            String providerId, ServiceRequestStatus status);
}

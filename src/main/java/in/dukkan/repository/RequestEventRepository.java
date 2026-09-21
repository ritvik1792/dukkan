package in.dukkan.repository;

import in.dukkan.domain.RequestEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestEventRepository extends JpaRepository<RequestEvent, String> {
    List<RequestEvent> findByRequestIdOrderByCreatedAtAsc(String requestId);
}

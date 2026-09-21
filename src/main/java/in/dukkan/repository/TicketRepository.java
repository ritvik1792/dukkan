package in.dukkan.repository;

import in.dukkan.domain.SupportTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<SupportTicket, String> {
    List<SupportTicket> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<SupportTicket> findByShopIdOrderByCreatedAtDesc(String shopId);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}

package in.dukkan.repository;

import in.dukkan.domain.Booking;
import in.dukkan.domain.BookingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Booking> findByProviderIdOrderByCreatedAtDesc(String providerId);

    List<Booking> findByProviderIdAndStatusOrderByCreatedAtDesc(String providerId, BookingStatus status);

    List<Booking> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
            String customerId, List<BookingStatus> statuses);
}

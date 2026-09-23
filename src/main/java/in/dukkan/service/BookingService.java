package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Booking;
import in.dukkan.domain.BookingStatus;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.Role;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.repository.BookingRepository;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ShopRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    public record CreateBookingInput(String serviceId, Instant scheduledStart, Instant scheduledEnd, String notes) {}

    public record StatusPatch(BookingStatus status) {}

    private final BookingRepository bookings;
    private final ProviderServiceRepository services;
    private final ShopRepository shops;

    public BookingService(
            BookingRepository bookings, ProviderServiceRepository services, ShopRepository shops) {
        this.bookings = bookings;
        this.services = services;
        this.shops = shops;
    }

    public List<Booking> listForCustomer(String customerId) {
        return bookings.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public List<Booking> listForProvider(String providerId, BookingStatus status) {
        if (status != null) {
            return bookings.findByProviderIdAndStatusOrderByCreatedAtDesc(providerId, status);
        }
        return bookings.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    public Booking get(String id) {
        return bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    @Transactional
    public Booking create(AppUser customer, CreateBookingInput input) {
        if (input.serviceId() == null || input.serviceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service is required");
        }
        if (input.scheduledStart() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled start is required");
        }
        ProviderService service = services.findById(input.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service is not active");
        }
        if (!service.isBookingEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not enabled for this service");
        }
        Shop provider = shops.findById(service.getProviderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        if (!provider.isBookingsAllowed() || !provider.isServicesAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider does not accept bookings");
        }
        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setId(Ids.next("bk"));
        booking.setCustomerId(customer.getId());
        booking.setProviderId(provider.getId());
        booking.setServiceId(service.getId());
        booking.setScheduledStart(input.scheduledStart());
        booking.setScheduledEnd(input.scheduledEnd() != null
                ? input.scheduledEnd()
                : service.getDurationMinutes() == null
                        ? null
                        : input.scheduledStart().plusSeconds(service.getDurationMinutes() * 60L));
        booking.setNotes(blankToNull(input.notes()));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        return bookings.save(booking);
    }

    @Transactional
    public Booking transition(AppUser actor, String bookingId, BookingStatus next) {
        Booking booking = get(bookingId);
        if (next == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        if (!booking.getStatus().canTransitionTo(next)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot transition booking from " + booking.getStatus() + " to " + next);
        }
        boolean isCustomer = booking.getCustomerId().equals(actor.getId());
        boolean isProvider = isProviderActor(actor, booking.getProviderId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;

        if (next == BookingStatus.CANCELLED) {
            if (!isCustomer && !isProvider && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (booking.getStatus() == BookingStatus.PENDING && !isCustomer && !isProvider && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else if (next == BookingStatus.CONFIRMED
                || next == BookingStatus.REJECTED
                || next == BookingStatus.COMPLETED) {
            if (!isProvider && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the provider can update this booking");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }

        booking.setStatus(next);
        booking.setUpdatedAt(Instant.now());
        return bookings.save(booking);
    }

    private boolean isProviderActor(AppUser actor, String providerId) {
        if (actor.getRole() == Role.ADMIN) {
            return true;
        }
        if (providerId.equals(actor.getShopId())) {
            return true;
        }
        return shops.findById(providerId)
                .map(shop -> shop.getOwnerUserId().equals(actor.getId()))
                .orElse(false);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

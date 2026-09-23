package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.Booking;
import in.dukkan.domain.BookingStatus;
import in.dukkan.domain.Role;
import in.dukkan.service.BookingService;
import in.dukkan.service.BookingService.CreateBookingInput;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class BookingController {

    public record CreateBookingBody(String serviceId, Instant scheduledStart, Instant scheduledEnd, String notes) {}

    public record BookingStatusBody(BookingStatus status) {}

    private final BookingService bookings;
    private final Access access;

    public BookingController(BookingService bookings, Access access) {
        this.bookings = bookings;
        this.access = access;
    }

    @PostMapping("/bookings")
    public Booking create(Authentication auth, @RequestBody CreateBookingBody body) {
        AppUser user = access.requireUser(auth);
        return bookings.create(
                user, new CreateBookingInput(body.serviceId(), body.scheduledStart(), body.scheduledEnd(), body.notes()));
    }

    @GetMapping("/bookings/mine")
    public List<Booking> mine(Authentication auth) {
        return bookings.listForCustomer(access.requireUser(auth).getId());
    }

    @GetMapping({"/seller/bookings", "/provider/bookings"})
    public List<Booking> providerList(
            Authentication auth,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) BookingStatus status) {
        AppUser user = access.requireSellerOrAdmin(auth);
        String id = resolveProviderId(user, providerId);
        return bookings.listForProvider(id, status);
    }

    @GetMapping("/bookings/{id}")
    public Booking get(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireUser(auth);
        Booking booking = bookings.get(id);
        assertCanView(user, booking);
        return booking;
    }

    @PatchMapping({"/bookings/{id}", "/seller/bookings/{id}", "/provider/bookings/{id}"})
    public Booking patch(
            Authentication auth, @PathVariable String id, @RequestBody BookingStatusBody body) {
        AppUser user = access.requireUser(auth);
        return bookings.transition(user, id, body.status());
    }

    private void assertCanView(AppUser user, Booking booking) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (booking.getCustomerId().equals(user.getId())) {
            return;
        }
        if (booking.getProviderId().equals(user.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private String resolveProviderId(AppUser user, String providerId) {
        if (providerId != null && !providerId.isBlank()) {
            return providerId;
        }
        if (user.getShopId() == null || user.getShopId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider id is required");
        }
        return user.getShopId();
    }
}

package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.Booking;
import in.dukkan.domain.BookingStatus;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.Role;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.BookingRepository;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.BookingService.CreateBookingInput;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    @Mock BookingRepository bookings;
    @Mock ProviderServiceRepository services;
    @Mock ShopRepository shops;

    BookingService service;

    AppUser customer;
    AppUser providerUser;
    Shop provider;
    ProviderService offering;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, services, shops);
        customer = user("buyer-1", Role.BUYER, null);
        providerUser = user("seller-1", Role.SELLER, "shop-1");
        provider = shop("shop-1", true, true);
        offering = offering("svc-1", "shop-1", true);

        when(bookings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(services.findById("svc-1")).thenReturn(Optional.of(offering));
        when(shops.findById("shop-1")).thenReturn(Optional.of(provider));
    }

    @Test
    void createStartsPending() {
        Booking booking = service.create(
                customer, new CreateBookingInput("svc-1", Instant.parse("2026-10-01T10:00:00Z"), null, "note"));
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals("buyer-1", booking.getCustomerId());
        assertEquals("shop-1", booking.getProviderId());
    }

    @Test
    void pendingCanConfirmRejectOrCancel() {
        Booking booking = pendingBooking();
        when(bookings.findById("bk-1")).thenReturn(Optional.of(booking));

        assertEquals(BookingStatus.CONFIRMED, service.transition(providerUser, "bk-1", BookingStatus.CONFIRMED).getStatus());

        booking.setStatus(BookingStatus.PENDING);
        assertEquals(BookingStatus.REJECTED, service.transition(providerUser, "bk-1", BookingStatus.REJECTED).getStatus());

        booking.setStatus(BookingStatus.PENDING);
        assertEquals(BookingStatus.CANCELLED, service.transition(customer, "bk-1", BookingStatus.CANCELLED).getStatus());
    }

    @Test
    void confirmedCanComplete() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findById("bk-1")).thenReturn(Optional.of(booking));
        assertEquals(BookingStatus.COMPLETED, service.transition(providerUser, "bk-1", BookingStatus.COMPLETED).getStatus());
    }

    @Test
    void rejectsIllegalTransition() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.COMPLETED);
        when(bookings.findById("bk-1")).thenReturn(Optional.of(booking));
        assertThrows(
                ResponseStatusException.class,
                () -> service.transition(providerUser, "bk-1", BookingStatus.CONFIRMED));
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setId("bk-1");
        booking.setCustomerId("buyer-1");
        booking.setProviderId("shop-1");
        booking.setServiceId("svc-1");
        booking.setScheduledStart(Instant.parse("2026-10-01T10:00:00Z"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(Instant.now());
        booking.setUpdatedAt(Instant.now());
        return booking;
    }

    private static AppUser user(String id, Role role, String shopId) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setShopId(shopId);
        user.setName(id);
        user.setEmail(id + "@test.com");
        return user;
    }

    private static Shop shop(String id, boolean services, boolean bookings) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setOwnerUserId("seller-1");
        shop.setStatus(ShopStatus.ACTIVE);
        shop.setServicesAllowed(services);
        shop.setBookingsAllowed(bookings);
        return shop;
    }

    private static ProviderService offering(String id, String providerId, boolean bookingEnabled) {
        ProviderService service = new ProviderService();
        service.setId(id);
        service.setProviderId(providerId);
        service.setName("Haircut");
        service.setStatus(ServiceStatus.ACTIVE);
        service.setBookingEnabled(bookingEnabled);
        service.setDurationMinutes(30);
        return service;
    }
}

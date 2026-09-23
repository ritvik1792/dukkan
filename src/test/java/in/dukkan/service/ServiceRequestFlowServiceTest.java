package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.Role;
import in.dukkan.domain.ServiceRequest;
import in.dukkan.domain.ServiceRequestStatus;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ServiceRequestRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.ServiceRequestFlowService.CreateServiceRequestInput;
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
class ServiceRequestFlowServiceTest {

    @Mock ServiceRequestRepository requests;
    @Mock ProviderServiceRepository services;
    @Mock ShopRepository shops;

    ServiceRequestFlowService flow;

    AppUser customer;
    AppUser providerUser;
    Shop provider;
    ProviderService offering;

    @BeforeEach
    void setUp() {
        flow = new ServiceRequestFlowService(requests, services, shops);
        customer = user("buyer-1", Role.BUYER, null);
        providerUser = user("seller-1", Role.SELLER, "shop-1");
        provider = shop("shop-1", true, true);
        offering = offering("svc-1", "shop-1");

        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(services.findById("svc-1")).thenReturn(Optional.of(offering));
        when(shops.findById("shop-1")).thenReturn(Optional.of(provider));
    }

    @Test
    void createStartsRequested() {
        ServiceRequest request = flow.create(
                customer,
                new CreateServiceRequestInput(
                        "svc-1", "12 MG Road", 28.6, 77.2, "AC not cooling", Instant.parse("2026-10-02T09:00:00Z"), "999"));
        assertEquals(ServiceRequestStatus.REQUESTED, request.getStatus());
        assertEquals("shop-1", request.getProviderId());
    }

    @Test
    void requestedCanAcceptRejectOrCancel() {
        ServiceRequest request = pending();
        when(requests.findById("srq-1")).thenReturn(Optional.of(request));

        assertEquals(
                ServiceRequestStatus.ACCEPTED,
                flow.transition(providerUser, "srq-1", ServiceRequestStatus.ACCEPTED).getStatus());

        request.setStatus(ServiceRequestStatus.REQUESTED);
        assertEquals(
                ServiceRequestStatus.REJECTED,
                flow.transition(providerUser, "srq-1", ServiceRequestStatus.REJECTED).getStatus());

        request.setStatus(ServiceRequestStatus.REQUESTED);
        assertEquals(
                ServiceRequestStatus.CANCELLED,
                flow.transition(customer, "srq-1", ServiceRequestStatus.CANCELLED).getStatus());
    }

    @Test
    void acceptedCanComplete() {
        ServiceRequest request = pending();
        request.setStatus(ServiceRequestStatus.ACCEPTED);
        when(requests.findById("srq-1")).thenReturn(Optional.of(request));
        assertEquals(
                ServiceRequestStatus.COMPLETED,
                flow.transition(providerUser, "srq-1", ServiceRequestStatus.COMPLETED).getStatus());
    }

    @Test
    void rejectsIllegalTransition() {
        ServiceRequest request = pending();
        request.setStatus(ServiceRequestStatus.COMPLETED);
        when(requests.findById("srq-1")).thenReturn(Optional.of(request));
        assertThrows(
                ResponseStatusException.class,
                () -> flow.transition(providerUser, "srq-1", ServiceRequestStatus.ACCEPTED));
    }

    private ServiceRequest pending() {
        ServiceRequest request = new ServiceRequest();
        request.setId("srq-1");
        request.setCustomerId("buyer-1");
        request.setProviderId("shop-1");
        request.setServiceId("svc-1");
        request.setCustomerAddress("12 MG Road");
        request.setStatus(ServiceRequestStatus.REQUESTED);
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        return request;
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

    private static Shop shop(String id, boolean services, boolean requestsAllowed) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setOwnerUserId("seller-1");
        shop.setStatus(ShopStatus.ACTIVE);
        shop.setServicesAllowed(services);
        shop.setServiceRequestsAllowed(requestsAllowed);
        return shop;
    }

    private static ProviderService offering(String id, String providerId) {
        ProviderService service = new ProviderService();
        service.setId(id);
        service.setProviderId(providerId);
        service.setName("AC Repair");
        service.setStatus(ServiceStatus.ACTIVE);
        service.setRequestEnabled(true);
        return service;
    }
}

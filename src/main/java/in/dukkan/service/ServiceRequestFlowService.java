package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.Role;
import in.dukkan.domain.ServiceRequest;
import in.dukkan.domain.ServiceRequestStatus;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ServiceRequestRepository;
import in.dukkan.repository.ShopRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceRequestFlowService {

    public record CreateServiceRequestInput(
            String serviceId,
            String customerAddress,
            Double customerLat,
            Double customerLng,
            String description,
            Instant preferredTime,
            String contactPhone) {}

    private final ServiceRequestRepository requests;
    private final ProviderServiceRepository services;
    private final ShopRepository shops;

    public ServiceRequestFlowService(
            ServiceRequestRepository requests,
            ProviderServiceRepository services,
            ShopRepository shops) {
        this.requests = requests;
        this.services = services;
        this.shops = shops;
    }

    public List<ServiceRequest> listForCustomer(String customerId) {
        return requests.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public List<ServiceRequest> listForProvider(String providerId, ServiceRequestStatus status) {
        if (status != null) {
            return requests.findByProviderIdAndStatusOrderByCreatedAtDesc(providerId, status);
        }
        return requests.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    public ServiceRequest get(String id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found"));
    }

    @Transactional
    public ServiceRequest create(AppUser customer, CreateServiceRequestInput input) {
        if (input.serviceId() == null || input.serviceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service is required");
        }
        if (input.customerAddress() == null || input.customerAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer location/address is required");
        }
        ProviderService service = services.findById(input.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service is not active");
        }
        if (!service.isRequestEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service requests are not enabled");
        }
        Shop provider = shops.findById(service.getProviderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        if (!provider.isServiceRequestsAllowed() || !provider.isServicesAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider does not accept service requests");
        }
        Instant now = Instant.now();
        ServiceRequest request = new ServiceRequest();
        request.setId(Ids.next("srq"));
        request.setCustomerId(customer.getId());
        request.setProviderId(provider.getId());
        request.setServiceId(service.getId());
        request.setCustomerAddress(input.customerAddress().trim());
        request.setCustomerLat(input.customerLat());
        request.setCustomerLng(input.customerLng());
        request.setDescription(blankToNull(input.description()));
        request.setPreferredTime(input.preferredTime());
        request.setContactPhone(blankToNull(
                input.contactPhone() != null ? input.contactPhone() : customer.getPhone()));
        request.setStatus(ServiceRequestStatus.REQUESTED);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        return requests.save(request);
    }

    @Transactional
    public ServiceRequest transition(AppUser actor, String requestId, ServiceRequestStatus next) {
        ServiceRequest request = get(requestId);
        if (next == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        if (!request.getStatus().canTransitionTo(next)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot transition service request from " + request.getStatus() + " to " + next);
        }
        boolean isCustomer = request.getCustomerId().equals(actor.getId());
        boolean isProvider = isProviderActor(actor, request.getProviderId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;

        if (next == ServiceRequestStatus.CANCELLED) {
            if (!isCustomer && !isProvider && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else if (next == ServiceRequestStatus.ACCEPTED
                || next == ServiceRequestStatus.REJECTED
                || next == ServiceRequestStatus.COMPLETED) {
            if (!isProvider && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the provider can update this request");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }

        request.setStatus(next);
        request.setUpdatedAt(Instant.now());
        return requests.save(request);
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

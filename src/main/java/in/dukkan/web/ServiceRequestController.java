package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.domain.ServiceRequest;
import in.dukkan.domain.ServiceRequestStatus;
import in.dukkan.service.ServiceRequestFlowService;
import in.dukkan.service.ServiceRequestFlowService.CreateServiceRequestInput;
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
public class ServiceRequestController {

    public record CreateBody(
            String serviceId,
            String customerAddress,
            Double customerLat,
            Double customerLng,
            String description,
            Instant preferredTime,
            String contactPhone) {}

    public record StatusBody(ServiceRequestStatus status) {}

    private final ServiceRequestFlowService flow;
    private final Access access;

    public ServiceRequestController(ServiceRequestFlowService flow, Access access) {
        this.flow = flow;
        this.access = access;
    }

    @PostMapping("/service-requests")
    public ServiceRequest create(Authentication auth, @RequestBody CreateBody body) {
        AppUser user = access.requireUser(auth);
        return flow.create(
                user,
                new CreateServiceRequestInput(
                        body.serviceId(),
                        body.customerAddress(),
                        body.customerLat(),
                        body.customerLng(),
                        body.description(),
                        body.preferredTime(),
                        body.contactPhone()));
    }

    @GetMapping("/service-requests/mine")
    public List<ServiceRequest> mine(Authentication auth) {
        return flow.listForCustomer(access.requireUser(auth).getId());
    }

    @GetMapping({"/seller/service-requests", "/provider/service-requests"})
    public List<ServiceRequest> providerList(
            Authentication auth,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) ServiceRequestStatus status) {
        AppUser user = access.requireSellerOrAdmin(auth);
        String id = resolveProviderId(user, providerId);
        return flow.listForProvider(id, status);
    }

    @GetMapping("/service-requests/{id}")
    public ServiceRequest get(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireUser(auth);
        ServiceRequest request = flow.get(id);
        assertCanView(user, request);
        return request;
    }

    @PatchMapping({
        "/service-requests/{id}",
        "/seller/service-requests/{id}",
        "/provider/service-requests/{id}"
    })
    public ServiceRequest patch(Authentication auth, @PathVariable String id, @RequestBody StatusBody body) {
        AppUser user = access.requireUser(auth);
        return flow.transition(user, id, body.status());
    }

    private void assertCanView(AppUser user, ServiceRequest request) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (request.getCustomerId().equals(user.getId())) {
            return;
        }
        if (request.getProviderId().equals(user.getShopId())) {
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

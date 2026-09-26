package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.Role;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ShopRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceCatalogService {

    public record ServiceWrite(
            String name,
            String description,
            String categoryId,
            BigDecimal price,
            BigDecimal startingPrice,
            Integer durationMinutes,
            String serviceArea,
            Boolean bookingEnabled,
            Boolean requestEnabled,
            String imageUrl,
            List<String> imageUrls,
            ServiceStatus status) {}

    private final ProviderServiceRepository services;
    private final ShopRepository shops;

    public ServiceCatalogService(ProviderServiceRepository services, ShopRepository shops) {
        this.services = services;
        this.shops = shops;
    }

    public List<ProviderService> listByProvider(String providerId, boolean activeOnly) {
        if (activeOnly) {
            return services.findByProviderIdAndStatus(providerId, ServiceStatus.ACTIVE);
        }
        return services.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    public List<ProviderService> listActive() {
        return services.findByStatus(ServiceStatus.ACTIVE);
    }

    public ProviderService get(String id) {
        return services.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    @Transactional
    public ProviderService create(AppUser actor, String providerId, ServiceWrite input) {
        Shop provider = requireManageableProvider(actor, providerId);
        assertCanPublishService(provider);
        if (input.name() == null || input.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
        }
        Instant now = Instant.now();
        ProviderService service = new ProviderService();
        service.setId(Ids.next("svc"));
        service.setProviderId(provider.getId());
        applyWrite(service, input, true);
        service.setCreatedAt(now);
        service.setUpdatedAt(now);
        return services.save(service);
    }

    @Transactional
    public ProviderService update(AppUser actor, String serviceId, ServiceWrite input) {
        ProviderService service = get(serviceId);
        Shop provider = requireManageableProvider(actor, service.getProviderId());
        assertCanPublishService(provider);
        applyWrite(service, input, false);
        service.setUpdatedAt(Instant.now());
        return services.save(service);
    }

    @Transactional
    public void delete(AppUser actor, String serviceId) {
        ProviderService service = get(serviceId);
        requireManageableProvider(actor, service.getProviderId());
        services.delete(service);
    }

    public void assertCanPublishService(Shop provider) {
        if (!provider.isServicesAllowed()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Provider is not allowed to publish services");
        }
        if (provider.getStatus() == ShopStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider is suspended");
        }
    }

    private void applyWrite(ProviderService service, ServiceWrite input, boolean creating) {
        if (creating || input.name() != null) {
            if (input.name() == null || input.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
            }
            service.setName(input.name().trim());
        }
        if (input.description() != null) {
            service.setDescription(blankToNull(input.description()));
        }
        if (input.categoryId() != null) {
            service.setCategoryId(blankToNull(input.categoryId()));
        }
        if (input.price() != null || creating) {
            service.setPrice(input.price());
        }
        if (input.startingPrice() != null || creating) {
            service.setStartingPrice(input.startingPrice());
        }
        if (input.durationMinutes() != null || creating) {
            service.setDurationMinutes(input.durationMinutes());
        }
        if (input.serviceArea() != null) {
            service.setServiceArea(blankToNull(input.serviceArea()));
        }
        if (input.bookingEnabled() != null) {
            service.setBookingEnabled(input.bookingEnabled());
        } else if (creating) {
            service.setBookingEnabled(false);
        }
        if (input.requestEnabled() != null) {
            service.setRequestEnabled(input.requestEnabled());
        } else if (creating) {
            service.setRequestEnabled(true);
        }
        if (input.imageUrl() != null) {
            service.setImageUrl(blankToNull(input.imageUrl()));
        }
        if (input.imageUrls() != null) {
            String main = service.getImageUrl() == null ? "" : service.getImageUrl().trim();
            LinkedHashSet<String> urls = new LinkedHashSet<>();
            for (String raw : input.imageUrls()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String url = raw.trim();
                if (!main.isEmpty() && url.equals(main)) {
                    continue;
                }
                urls.add(url);
            }
            List<String> images = service.getImageUrls();
            images.clear();
            if (!creating && service.getId() != null && !urls.isEmpty()) {
                services.saveAndFlush(service);
            }
            images.addAll(urls);
        }
        if (input.status() != null) {
            service.setStatus(input.status());
        } else if (creating) {
            service.setStatus(ServiceStatus.ACTIVE);
        }
    }

    private Shop requireManageableProvider(AppUser actor, String providerId) {
        Shop provider = shops.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        if (actor.getRole() == Role.ADMIN) {
            return provider;
        }
        if (!provider.getOwnerUserId().equals(actor.getId())
                && (actor.getShopId() == null || !actor.getShopId().equals(provider.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return provider;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

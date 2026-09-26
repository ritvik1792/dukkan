package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopEmployee;
import in.dukkan.domain.ShopEmployeeRole;
import in.dukkan.domain.ShopTransport;
import in.dukkan.domain.ShopTransportKind;
import in.dukkan.repository.ShopEmployeeRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.repository.ShopTransportRepository;
import in.dukkan.web.dto.ShopDtos.EmployeeWrite;
import in.dukkan.web.dto.ShopDtos.ShopPatch;
import in.dukkan.web.dto.ShopDtos.ShopView;
import in.dukkan.web.dto.ShopDtos.TransportWrite;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ShopController {

    private final ShopRepository shops;
    private final ShopEmployeeRepository employees;
    private final ShopTransportRepository transport;
    private final ShopViews shopViews;
    private final Access access;

    public ShopController(
            ShopRepository shops,
            ShopEmployeeRepository employees,
            ShopTransportRepository transport,
            ShopViews shopViews,
            Access access) {
        this.shops = shops;
        this.employees = employees;
        this.transport = transport;
        this.shopViews = shopViews;
        this.access = access;
    }

    @PatchMapping({"/api/shops/{id}", "/api/seller/shops/{id}", "/api/admin/shops/{id}"})
    @Transactional
    public ShopView patchShop(Authentication auth, @PathVariable String id, @RequestBody ShopPatch request) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = shops.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        assertCanManage(user, shop);
        if (request.partnerDeliveryEnabled() != null) {
            shop.setPartnerDeliveryEnabled(request.partnerDeliveryEnabled());
        }
        if (request.shopDeliveryEnabled() != null) {
            shop.setShopDeliveryEnabled(request.shopDeliveryEnabled());
        }
        if (request.partnerDeliveryFee() != null) {
            shop.setPartnerDeliveryFee(request.partnerDeliveryFee());
        }
        if (request.shopDeliveryFee() != null) {
            shop.setShopDeliveryFee(request.shopDeliveryFee());
        }
        if (request.minOrderAmount() != null) {
            shop.setMinOrderAmount(request.minOrderAmount());
        }
        if (request.isOpen() != null) {
            shop.setOpen(request.isOpen());
        }
        if (request.openTime() != null && !request.openTime().isBlank()) {
            shop.setOpenTime(request.openTime().trim());
        }
        if (request.closeTime() != null && !request.closeTime().isBlank()) {
            shop.setCloseTime(request.closeTime().trim());
        }
        if (request.notificationsEnabled() != null) {
            shop.setNotificationsEnabled(request.notificationsEnabled());
        }
        if (request.notifyOrderReceived() != null) {
            shop.setNotifyOrderReceived(request.notifyOrderReceived());
        }
        if (request.notifyOrderStatus() != null) {
            shop.setNotifyOrderStatus(request.notifyOrderStatus());
        }
        if (request.notifyStockConfirmation() != null) {
            shop.setNotifyStockConfirmation(request.notifyStockConfirmation());
        }
        if (request.name() != null && !request.name().isBlank()) {
            shop.setName(request.name().trim());
        }
        if (request.description() != null) {
            shop.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.address() != null && !request.address().isBlank()) {
            shop.setAddress(request.address().trim());
        }
        if (request.lat() != null) {
            shop.setLat(request.lat());
        }
        if (request.lng() != null) {
            shop.setLng(request.lng());
        }
        if (request.imageUrl() != null) {
            shop.setImageUrl(request.imageUrl().isBlank() ? null : request.imageUrl().trim());
        }
        if (request.categoryIds() != null) {
            if (shop.getCategoryIds() == null) {
                shop.setCategoryIds(new HashSet<>());
            }
            shop.getCategoryIds().clear();
            for (String categoryId : request.categoryIds()) {
                if (categoryId != null && !categoryId.isBlank()) {
                    shop.getCategoryIds().add(categoryId.trim());
                }
            }
        }
        if (request.serviceArea() != null) {
            shop.setServiceArea(request.serviceArea().isBlank() ? null : request.serviceArea().trim());
        }
        if (request.profession() != null) {
            shop.setProfession(request.profession().isBlank() ? null : request.profession().trim());
        }
        if (request.status() != null
                || request.providerType() != null
                || request.productsAllowed() != null
                || request.servicesAllowed() != null
                || request.bookingsAllowed() != null
                || request.serviceRequestsAllowed() != null
                || request.ordersAllowed() != null
                || request.quickDeliveryAllowed() != null) {
            if (!access.isAdmin(user)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can change provider status or capabilities");
            }
            if (request.status() != null) {
                shop.setStatus(request.status());
            }
            if (request.providerType() != null) {
                shop.setProviderType(request.providerType());
            }
            if (request.productsAllowed() != null) {
                shop.setProductsAllowed(request.productsAllowed());
            }
            if (request.servicesAllowed() != null) {
                shop.setServicesAllowed(request.servicesAllowed());
            }
            if (request.bookingsAllowed() != null) {
                shop.setBookingsAllowed(request.bookingsAllowed());
            }
            if (request.serviceRequestsAllowed() != null) {
                shop.setServiceRequestsAllowed(request.serviceRequestsAllowed());
            }
            if (request.ordersAllowed() != null) {
                shop.setOrdersAllowed(request.ordersAllowed());
            }
            if (request.quickDeliveryAllowed() != null) {
                shop.setQuickDeliveryAllowed(request.quickDeliveryAllowed());
            }
        }
        return shopViews.toView(shops.save(shop));
    }

    @PostMapping({"/api/seller/employees", "/api/admin/employees"})
    @Transactional
    public ShopEmployee createEmployee(
            Authentication auth, @RequestBody EmployeeWrite request, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveShop(user, firstNonBlank(request.shopId(), shopId));
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        ShopEmployee employee = new ShopEmployee();
        employee.setId(Ids.next("emp"));
        employee.setShopId(shop.getId());
        employee.setName(request.name().trim());
        employee.setRole(ShopEmployeeRole.fromJson(request.role()));
        employee.setPhone(blankToNull(request.phone()));
        employee.setAvailable(request.available() == null || request.available());
        return employees.save(employee);
    }

    @PatchMapping({"/api/seller/employees/{id}", "/api/admin/employees/{id}"})
    @Transactional
    public ShopEmployee patchEmployee(Authentication auth, @PathVariable String id, @RequestBody EmployeeWrite request) {
        AppUser user = access.requireSellerOrAdmin(auth);
        ShopEmployee employee = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        assertCanManage(user, requireShop(employee.getShopId()));
        if (request.name() != null && !request.name().isBlank()) {
            employee.setName(request.name().trim());
        }
        if (request.role() != null) {
            employee.setRole(ShopEmployeeRole.fromJson(request.role()));
        }
        if (request.phone() != null) {
            employee.setPhone(blankToNull(request.phone()));
        }
        if (request.available() != null) {
            employee.setAvailable(request.available());
        }
        return employees.save(employee);
    }

    @DeleteMapping({"/api/seller/employees/{id}", "/api/admin/employees/{id}"})
    @Transactional
    public void deleteEmployee(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireSellerOrAdmin(auth);
        ShopEmployee employee = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        assertCanManage(user, requireShop(employee.getShopId()));
        employees.delete(employee);
    }

    @PostMapping({"/api/seller/transport", "/api/admin/transport"})
    @Transactional
    public ShopTransport createTransport(
            Authentication auth, @RequestBody TransportWrite request, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveShop(user, firstNonBlank(request.shopId(), shopId));
        ShopTransport vehicle = new ShopTransport();
        vehicle.setId(Ids.next("veh"));
        vehicle.setShopId(shop.getId());
        vehicle.setKind(ShopTransportKind.fromJson(request.kind()));
        String label = request.label() == null || request.label().isBlank()
                ? titleCase(vehicle.getKind().toJson())
                : request.label().trim();
        vehicle.setLabel(label);
        vehicle.setRegistration(blankToNull(request.registration()));
        vehicle.setCapacityKg(request.capacityKg());
        vehicle.setAvailable(request.available() == null || request.available());
        return transport.save(vehicle);
    }

    @PatchMapping({"/api/seller/transport/{id}", "/api/admin/transport/{id}"})
    @Transactional
    public ShopTransport patchTransport(Authentication auth, @PathVariable String id, @RequestBody TransportWrite request) {
        AppUser user = access.requireSellerOrAdmin(auth);
        ShopTransport vehicle = transport.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        assertCanManage(user, requireShop(vehicle.getShopId()));
        if (request.kind() != null) {
            vehicle.setKind(ShopTransportKind.fromJson(request.kind()));
        }
        if (request.label() != null && !request.label().isBlank()) {
            vehicle.setLabel(request.label().trim());
        }
        if (request.registration() != null) {
            vehicle.setRegistration(blankToNull(request.registration()));
        }
        if (request.capacityKg() != null) {
            vehicle.setCapacityKg(request.capacityKg());
        }
        if (request.available() != null) {
            vehicle.setAvailable(request.available());
        }
        return transport.save(vehicle);
    }

    @DeleteMapping({"/api/seller/transport/{id}", "/api/admin/transport/{id}"})
    @Transactional
    public void deleteTransport(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireSellerOrAdmin(auth);
        ShopTransport vehicle = transport.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        assertCanManage(user, requireShop(vehicle.getShopId()));
        transport.delete(vehicle);
    }

    private Shop resolveShop(AppUser user, String requestedShopId) {
        if (requestedShopId != null && !requestedShopId.isBlank()) {
            Shop shop = requireShop(requestedShopId);
            assertCanManage(user, shop);
            return shop;
        }
        if (user.getShopId() != null && !user.getShopId().isBlank()) {
            Shop shop = requireShop(user.getShopId());
            assertCanManage(user, shop);
            return shop;
        }
        return shops.findByOwnerUserId(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No shop for this account"));
    }

    private Shop requireShop(String shopId) {
        return shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
    }

    private void assertCanManage(AppUser user, Shop shop) {
        if (user.getRole() == Role.ADMIN || shop.getOwnerUserId().equals(user.getId())
                || shop.getId().equals(user.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String titleCase(String kind) {
        if (kind == null || kind.isBlank()) {
            return "Vehicle";
        }
        return kind.substring(0, 1).toUpperCase() + kind.substring(1).replace('_', ' ');
    }
}

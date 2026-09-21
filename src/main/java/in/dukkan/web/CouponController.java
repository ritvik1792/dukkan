package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Coupon;
import in.dukkan.domain.Role;
import in.dukkan.repository.CouponRepository;
import in.dukkan.repository.ShopRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    public record CouponRequest(
            @NotBlank String shopId,
            @NotBlank String code,
            @NotBlank String label,
            int discountPercent,
            BigDecimal minOrderAmount,
            Boolean active) {}

    private final CouponRepository coupons;
    private final ShopRepository shops;
    private final Access access;

    public CouponController(CouponRepository coupons, ShopRepository shops, Access access) {
        this.coupons = coupons;
        this.shops = shops;
        this.access = access;
    }

    @GetMapping
    public List<Coupon> list(@RequestParam(required = false) String shopId) {
        if (shopId != null && !shopId.isBlank()) {
            return coupons.findByShopId(shopId);
        }
        return coupons.findAllByOrderByIdDesc();
    }

    @PostMapping
    @Transactional
    public Coupon create(Authentication auth, @Valid @RequestBody CouponRequest request) {
        assertCanEdit(access.requireUser(auth), request.shopId());
        Coupon coupon = new Coupon();
        coupon.setId(Ids.next("cpn"));
        apply(coupon, request);
        return coupons.save(coupon);
    }

    @PutMapping("/{id}")
    @Transactional
    public Coupon update(Authentication auth, @PathVariable String id, @Valid @RequestBody CouponRequest request) {
        Coupon coupon = coupons.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanEdit(access.requireUser(auth), coupon.getShopId());
        apply(coupon, request);
        return coupons.save(coupon);
    }

    private void apply(Coupon coupon, CouponRequest request) {
        coupon.setShopId(request.shopId());
        coupon.setCode(request.code().trim().toUpperCase());
        coupon.setLabel(request.label().trim());
        coupon.setDiscountPercent(request.discountPercent());
        coupon.setMinOrderAmount(request.minOrderAmount() == null ? BigDecimal.ZERO : request.minOrderAmount());
        coupon.setActive(request.active() == null || request.active());
    }

    private void assertCanEdit(AppUser user, String shopId) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        boolean owns = shops.findById(shopId)
                .map(shop -> shop.getOwnerUserId().equals(user.getId()))
                .orElse(false);
        if (!owns) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}

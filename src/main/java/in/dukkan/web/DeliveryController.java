package in.dukkan.web;

import in.dukkan.domain.Shop;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.GeocodeService;
import in.dukkan.service.ServiceabilityService;
import in.dukkan.service.ServiceabilityService.Verdict;
import in.dukkan.web.dto.GeoDtos.ReverseGeocodeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    public record CheckRequest(@NotBlank String pinCode, @NotEmpty List<String> shopIds) {}

    public record CheckResponse(
            String pinCode, double lat, double lng, String label, List<Verdict> results) {}

    private final GeocodeService geocode;
    private final ShopRepository shops;
    private final ServiceabilityService serviceability;

    public DeliveryController(GeocodeService geocode, ShopRepository shops, ServiceabilityService serviceability) {
        this.geocode = geocode;
        this.shops = shops;
        this.serviceability = serviceability;
    }

    @PostMapping("/check")
    public CheckResponse check(@Valid @RequestBody CheckRequest request) {
        ReverseGeocodeResponse point = geocode.searchPostalCode(request.pinCode());
        List<Verdict> results = new ArrayList<>();
        for (String shopId : request.shopIds()) {
            if (shopId == null || shopId.isBlank()) {
                continue;
            }
            Shop shop = shops.findById(shopId).orElse(null);
            if (shop == null) {
                continue;
            }
            results.add(serviceability.evaluate(shop, point.lat(), point.lng()));
        }
        String label = point.formattedAddress() == null || point.formattedAddress().isBlank()
                ? request.pinCode().trim()
                : point.formattedAddress();
        return new CheckResponse(request.pinCode().trim(), point.lat(), point.lng(), label, results);
    }
}
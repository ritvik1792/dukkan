package in.dukkan.web;

import in.dukkan.repository.ShopRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ShopRepository shops;

    public HealthController(ShopRepository shops) {
        this.shops = shops;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "dukkan");
        try {
            long shopCount = shops.count();
            body.put("status", "ok");
            body.put("database", "up");
            body.put("shopCount", shopCount);
        } catch (Exception e) {
            body.put("status", "degraded");
            body.put("database", "down");
            body.put("shopCount", 0);
        }
        return body;
    }
}

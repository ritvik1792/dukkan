package in.dukkan.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductRequestScheduler {

    private final ProductRequestService productRequests;

    public ProductRequestScheduler(ProductRequestService productRequests) {
        this.productRequests = productRequests;
    }

    @Scheduled(fixedDelayString = "${app.requests.scheduler-ms:15000}")
    public void tick() {
        productRequests.expireStale();
        productRequests.advanceDueWaves();
    }
}

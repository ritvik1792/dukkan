package in.dukkan.service;

import in.dukkan.domain.RequestShopStatus;
import in.dukkan.repository.RequestShopRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Lightweight read-side helpers. request_shops rows remain the source of truth;
 * nothing destructive is written here.
 */
@Service
public class StatisticsService {

    private final RequestShopRepository requestShops;

    public StatisticsService(RequestShopRepository requestShops) {
        this.requestShops = requestShops;
    }

    public Map<String, Object> shopResponseSummary(String shopId) {
        long accepted = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.ACCEPTED);
        long declined = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.DECLINED);
        long noReply = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.NO_REPLY);
        long notified = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.NOTIFIED);
        long totalResponded = accepted + declined;
        double acceptRate = totalResponded == 0 ? 0.5 : (double) accepted / totalResponded;
        return Map.of(
                "shopId", shopId,
                "accepted", accepted,
                "declined", declined,
                "noReply", noReply,
                "notifiedOpen", notified,
                "acceptRate", acceptRate);
    }

    /** Higher is better for ranking: prefers shops that accept over no-reply. */
    public double historyScore(String shopId) {
        long accepted = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.ACCEPTED);
        long declined = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.DECLINED);
        long noReply = requestShops.countByShopIdAndStatus(shopId, RequestShopStatus.NO_REPLY);
        long total = accepted + declined + noReply;
        if (total == 0) {
            return 0.5;
        }
        return (accepted + 0.25 * declined) / total;
    }
}

package in.dukkan.service;

import in.dukkan.domain.Offer;
import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.RequestShop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * No push/FCM and no notifications table. Seller alerts are composed client-side from
 * GET /api/merchant/requests (see dukkan-ui NotificationWatcher). This hook stays so
 * wave notify still has a stable extension point.
 */
@Service
public class InboxNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(InboxNotificationService.class);

    @Override
    public void notifyMerchantOfRequest(ProductRequest request, RequestShop requestShop) {
        log.info(
                "Merchant request ready for inbox requestId={} shopId={} requestShopId={} maxBudget={}",
                request.getId(),
                requestShop.getShopId(),
                requestShop.getId(),
                request.getMaxBudget());
    }

    @Override
    public void notifyCustomerOfOffer(ProductRequest request, Offer offer) {
        log.info(
                "Customer notify requestId={} offerId={} shopId={}",
                request.getId(),
                offer.getId(),
                offer.getShopId());
    }
}

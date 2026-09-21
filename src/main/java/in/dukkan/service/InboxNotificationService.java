package in.dukkan.service;

import in.dukkan.domain.Offer;
import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.RequestShop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * V1: no push/FCM. Seller inbox is derived from request_shops with status NOTIFIED+.
 * This bean is still invoked so business logic has a stable hook.
 */
@Service
public class InboxNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(InboxNotificationService.class);

    @Override
    public void notifyMerchantOfRequest(ProductRequest request, RequestShop requestShop) {
        log.info(
                "Merchant notify requestId={} shopId={} requestShopId={}",
                request.getId(),
                requestShop.getShopId(),
                requestShop.getId());
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

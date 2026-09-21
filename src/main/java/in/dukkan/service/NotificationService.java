package in.dukkan.service;

import in.dukkan.domain.Offer;
import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.RequestShop;

public interface NotificationService {
    void notifyMerchantOfRequest(ProductRequest request, RequestShop requestShop);

    void notifyCustomerOfOffer(ProductRequest request, Offer offer);
}

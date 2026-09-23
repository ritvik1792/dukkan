package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.ServiceCatalogService.ServiceWrite;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceCatalogServiceTest {

    @Mock ProviderServiceRepository services;
    @Mock ShopRepository shops;

    ServiceCatalogService catalog;

    AppUser seller;
    Shop shop;

    @BeforeEach
    void setUp() {
        catalog = new ServiceCatalogService(services, shops);
        seller = new AppUser();
        seller.setId("seller-1");
        seller.setRole(Role.SELLER);
        seller.setShopId("shop-1");
        shop = new Shop();
        shop.setId("shop-1");
        shop.setOwnerUserId("seller-1");
        shop.setStatus(ShopStatus.ACTIVE);
        shop.setServicesAllowed(false);
        when(shops.findById("shop-1")).thenReturn(Optional.of(shop));
        when(services.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shopWithoutServiceAllowedCannotPublish() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> catalog.create(
                        seller,
                        "shop-1",
                        new ServiceWrite(
                                "AC Repair",
                                "Fix AC",
                                "ac-repair",
                                null,
                                null,
                                60,
                                "South Delhi",
                                true,
                                true,
                                null,
                                null,
                                null)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(services, never()).save(any());
    }

    @Test
    void shopWithServiceAllowedCanPublish() {
        shop.setServicesAllowed(true);
        var created = catalog.create(
                seller,
                "shop-1",
                new ServiceWrite(
                        "AC Repair",
                        "Fix AC",
                        "ac-repair",
                        null,
                        null,
                        60,
                        "South Delhi",
                        true,
                        true,
                        null,
                        null,
                        null));
        assertEquals("AC Repair", created.getName());
        assertEquals("shop-1", created.getProviderId());
        verify(services).save(any());
    }
}

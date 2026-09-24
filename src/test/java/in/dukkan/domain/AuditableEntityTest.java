package in.dukkan.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import in.dukkan.common.ClientSourceHolder;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditableEntityTest {

    @AfterEach
    void tearDown() {
        ClientSourceHolder.clear();
    }

    @Test
    void defaultsToOnlineWhenNoHeaderProvided() {
        CatalogProduct product = new CatalogProduct();
        product.onPrePersist();

        assertEquals("O", product.getSource());
        assertNotNull(product.getLastUpdated());
    }

    @Test
    void capturesMobileSourceFromContext() {
        ClientSourceHolder.setSource("M");

        CatalogProduct product = new CatalogProduct();
        product.onPrePersist();

        assertEquals("M", product.getSource());
        assertNotNull(product.getLastUpdated());
    }

    @Test
    void updatesLastUpdatedTimestampOnPreUpdate() {
        CatalogProduct product = new CatalogProduct();
        Instant initial = Instant.now().minusSeconds(10);
        product.setLastUpdated(initial);

        product.onPreUpdate();

        assertNotNull(product.getLastUpdated());
        org.junit.jupiter.api.Assertions.assertTrue(product.getLastUpdated().isAfter(initial));
    }
}

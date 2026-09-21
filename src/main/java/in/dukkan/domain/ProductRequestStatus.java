package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductRequestStatus {
    OPEN,
    AWAITING_OFFERS,
    OFFERS_READY,
    SELECTED,
    ORDERED,
    EXPIRED,
    CANCELLED;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ProductRequestStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }

    public boolean isTerminal() {
        return this == ORDERED || this == EXPIRED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == OPEN || this == AWAITING_OFFERS || this == OFFERS_READY || this == SELECTED;
    }

    public boolean acceptsMerchantResponse() {
        return this == OPEN || this == AWAITING_OFFERS || this == OFFERS_READY;
    }
}

package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OfferStatus {
    ACTIVE,
    EXPIRED,
    WITHDRAWN,
    SELECTED,
    REJECTED;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static OfferStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}

package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TagKind {
    SALE,
    COUPON,
    OFFER,
    BADGE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TagKind fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}

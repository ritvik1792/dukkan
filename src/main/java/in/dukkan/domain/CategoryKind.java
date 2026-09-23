package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CategoryKind {
    PRODUCT,
    SERVICE,
    BOTH;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static CategoryKind fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}

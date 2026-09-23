package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ServiceStatus {
    ACTIVE,
    INACTIVE;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ServiceStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}

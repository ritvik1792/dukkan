package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ApprovalStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}

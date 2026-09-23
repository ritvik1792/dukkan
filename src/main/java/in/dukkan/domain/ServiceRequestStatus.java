package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ServiceRequestStatus {
    REQUESTED,
    ACCEPTED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ServiceRequestStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    public boolean canTransitionTo(ServiceRequestStatus next) {
        if (next == null || this == next) {
            return false;
        }
        return switch (this) {
            case REQUESTED -> next == ACCEPTED || next == REJECTED || next == CANCELLED;
            case ACCEPTED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }
}

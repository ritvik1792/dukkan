package in.dukkan.domain;

import in.dukkan.common.ClientSourceHolder;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

@MappedSuperclass
public abstract class AuditableEntity {

    /**
     * Creation source: 'M' for Mobile App, 'O' for Online (Web).
     */
    @Column(name = "source", length = 1, nullable = false)
    private String source;

    /**
     * Timestamp when the record was created or last updated.
     */
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @PrePersist
    public void onPrePersist() {
        if (this.source == null || this.source.isBlank()) {
            this.source = ClientSourceHolder.getSource();
        } else if ("M".equalsIgnoreCase(this.source)) {
            this.source = "M";
        } else {
            this.source = "O";
        }
        if (this.lastUpdated == null) {
            this.lastUpdated = Instant.now();
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.lastUpdated = Instant.now();
    }

    public String getSource() {
        return (source != null && !source.isBlank()) ? source : "O";
    }

    public void setSource(String source) {
        if (source != null && "M".equalsIgnoreCase(source.trim())) {
            this.source = "M";
        } else if (source != null && "O".equalsIgnoreCase(source.trim())) {
            this.source = "O";
        } else if (source != null && !source.isBlank()) {
            this.source = source.trim().substring(0, 1).toUpperCase();
        }
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "normalized_events")
public class NormalizedEvent {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Column(name = "posted_at", nullable = false)
    private LocalDate postedAt;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(length = 500)
    private String counterparty;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "raw_ref", columnDefinition = "text")
    private String rawRef;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(nullable = false)
    private int ordinal;

    protected NormalizedEvent() {
    }

    public NormalizedEvent(
            UUID id,
            UUID jobId,
            String externalId,
            LocalDate postedAt,
            BigDecimal amount,
            String currency,
            String direction,
            String counterparty,
            String description,
            String rawRef,
            BigDecimal confidence,
            int ordinal
    ) {
        this.id = id;
        this.jobId = jobId;
        this.externalId = externalId;
        this.postedAt = postedAt;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.counterparty = counterparty;
        this.description = description;
        this.rawRef = rawRef;
        this.confidence = confidence;
        this.ordinal = ordinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getExternalId() {
        return externalId;
    }

    public LocalDate getPostedAt() {
        return postedAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDirection() {
        return direction;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public String getDescription() {
        return description;
    }

    public String getRawRef() {
        return rawRef;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public int getOrdinal() {
        return ordinal;
    }
}

package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_ledger")
public class CreditLedgerEntry {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private long delta;

    @Column(nullable = false, length = 120)
    private String reason;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditLedgerEntry() {
    }

    public CreditLedgerEntry(UUID id, UUID accountId, long delta, String reason, UUID jobId, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.delta = delta;
        this.reason = reason;
        this.jobId = jobId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

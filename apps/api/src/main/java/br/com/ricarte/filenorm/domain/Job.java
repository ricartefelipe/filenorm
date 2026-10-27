package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 40)
    private String format;

    @Column(length = 40)
    private String preset;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "blob_path", nullable = false, length = 1000)
    private String blobPath;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column
    private Integer pages;

    @Column(name = "credits_charged", nullable = false)
    private int creditsCharged;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "balance_status", length = 20)
    private String balanceStatus;

    @Column(name = "balance_source", length = 40)
    private String balanceSource;

    @Column(name = "balance_reason", length = 60)
    private String balanceReason;

    @Column(name = "opening_balance", precision = 18, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 18, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "computed_closing_balance", precision = 18, scale = 2)
    private BigDecimal computedClosingBalance;

    @Column(name = "balance_difference", precision = 18, scale = 2)
    private BigDecimal balanceDifference;

    protected Job() {
    }

    public Job(
            UUID id,
            UUID accountId,
            String status,
            String format,
            String preset,
            String originalFilename,
            String contentType,
            long byteSize,
            String blobPath,
            Instant createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.status = status;
        this.format = format;
        this.preset = preset;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.blobPath = blobPath;
        this.creditsCharged = 0;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFormat() {
        return format;
    }

    public String getPreset() {
        return preset;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getBlobPath() {
        return blobPath;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public int getCreditsCharged() {
        return creditsCharged;
    }

    public void setCreditsCharged(int creditsCharged) {
        this.creditsCharged = creditsCharged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public void setCounts(int eventCount, int rejectedCount, int skippedCount, int warningCount) {
        this.eventCount = eventCount;
        this.rejectedCount = rejectedCount;
        this.skippedCount = skippedCount;
        this.warningCount = warningCount;
    }

    public String getBalanceStatus() {
        return balanceStatus;
    }

    public String getBalanceSource() {
        return balanceSource;
    }

    public String getBalanceReason() {
        return balanceReason;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public BigDecimal getComputedClosingBalance() {
        return computedClosingBalance;
    }

    public BigDecimal getBalanceDifference() {
        return balanceDifference;
    }

    public void setBalanceCheck(
            String status,
            String source,
            String reason,
            BigDecimal opening,
            BigDecimal closing,
            BigDecimal computedClosing,
            BigDecimal difference
    ) {
        this.balanceStatus = status;
        this.balanceSource = source;
        this.balanceReason = reason;
        this.openingBalance = opening;
        this.closingBalance = closing;
        this.computedClosingBalance = computedClosing;
        this.balanceDifference = difference;
    }
}

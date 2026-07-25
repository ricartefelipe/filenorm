package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}

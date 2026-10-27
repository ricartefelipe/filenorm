package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "job_issues")
public class JobIssue {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "record_ref", nullable = false, length = 80)
    private String recordRef;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private int ordinal;

    protected JobIssue() {
    }

    public JobIssue(UUID id, UUID jobId, String severity, String code, String recordRef, String detail, int ordinal) {
        this.id = id;
        this.jobId = jobId;
        this.severity = severity;
        this.code = code;
        this.recordRef = recordRef;
        this.detail = detail;
        this.ordinal = ordinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getRecordRef() {
        return recordRef;
    }

    public String getDetail() {
        return detail;
    }

    public int getOrdinal() {
        return ordinal;
    }
}

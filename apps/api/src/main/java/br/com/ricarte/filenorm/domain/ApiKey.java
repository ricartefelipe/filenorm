package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApiKey() {
    }

    public ApiKey(
            UUID id,
            UUID accountId,
            String name,
            String keyPrefix,
            String keyHash,
            Instant createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }

    public boolean active() {
        return revokedAt == null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

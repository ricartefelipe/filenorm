package br.com.ricarte.filenorm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(length = 200)
    private String name;

    @Column(name = "credits_balance", nullable = false)
    private long creditsBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Account() {
    }

    public Account(UUID id, String email, String name, long creditsBalance, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.creditsBalance = creditsBalance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreditsBalance() {
        return creditsBalance;
    }

    public void setCreditsBalance(long creditsBalance) {
        this.creditsBalance = creditsBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}

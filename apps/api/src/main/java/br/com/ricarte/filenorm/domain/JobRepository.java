package br.com.ricarte.filenorm.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findTop50ByAccountIdOrderByCreatedAtDesc(UUID accountId);

    long countByStatus(String status);

    /**
     * Candidatos: jobs na fila e jobs "running" cujo lease expirou (worker morreu ou travou).
     * Lease nulo em "running" vem de versões sem lease e também é considerado expirado.
     */
    @Query(value = """
            SELECT id FROM jobs
            WHERE (status = 'queued'
                   OR (status = 'running' AND (lease_expires_at IS NULL OR lease_expires_at < :now)))
              AND attempts < :maxAttempts
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> claimCandidateIds(
            @Param("limit") int limit,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE jobs
            SET status = 'running', attempts = attempts + 1, started_at = :now, lease_expires_at = :leaseUntil
            WHERE id IN (:ids)
            """, nativeQuery = true)
    int markRunning(@Param("ids") List<UUID> ids, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE jobs
            SET status = 'failed', error_code = 'max_attempts_exceeded',
                error_message = 'processing did not finish within the allowed attempts',
                credits_charged = 0, finished_at = :now, lease_expires_at = NULL
            WHERE status = 'running'
              AND (lease_expires_at IS NULL OR lease_expires_at < :now)
              AND attempts >= :maxAttempts
            """, nativeQuery = true)
    int failExhausted(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.id = :id")
    Optional<Job> findForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT id FROM jobs
            WHERE status IN ('succeeded', 'partial', 'failed') AND finished_at < :cutoff
            ORDER BY finished_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findFinishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}

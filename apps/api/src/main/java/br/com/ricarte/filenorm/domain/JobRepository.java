package br.com.ricarte.filenorm.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Query(value = """
            SELECT id FROM jobs
            WHERE status = 'queued'
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> claimCandidateIds(@Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE jobs
            SET status = 'running', started_at = :startedAt
            WHERE id IN (:ids) AND status = 'queued'
            """, nativeQuery = true)
    int markRunning(@Param("ids") List<UUID> ids, @Param("startedAt") java.time.Instant startedAt);
}

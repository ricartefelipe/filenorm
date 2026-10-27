package br.com.ricarte.filenorm.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobIssueRepository extends JpaRepository<JobIssue, UUID> {

    List<JobIssue> findByJobIdOrderByOrdinalAsc(UUID jobId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM JobIssue i WHERE i.jobId = :jobId")
    int deleteByJobId(@Param("jobId") UUID jobId);
}

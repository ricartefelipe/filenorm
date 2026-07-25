package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.domain.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobClaimer {

    private final JobRepository jobRepository;

    public JobClaimer(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public List<UUID> claim(int batchSize) {
        List<UUID> ids = jobRepository.claimCandidateIds(batchSize);
        if (ids.isEmpty()) {
            return ids;
        }
        jobRepository.markRunning(ids, Instant.now());
        return ids;
    }
}

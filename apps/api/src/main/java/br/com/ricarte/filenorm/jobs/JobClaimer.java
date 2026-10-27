package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobClaimer {

    private static final Logger log = LoggerFactory.getLogger(JobClaimer.class);

    private final JobRepository jobRepository;
    private final FilenormProperties properties;

    public JobClaimer(JobRepository jobRepository, FilenormProperties properties) {
        this.jobRepository = jobRepository;
        this.properties = properties;
    }

    @Transactional
    public List<JobClaim> claim(int batchSize) {
        Instant now = Instant.now();
        int maxAttempts = properties.worker().maxAttempts();
        int exhausted = jobRepository.failExhausted(now, maxAttempts);
        if (exhausted > 0) {
            log.warn("{} job(s) failed after {} attempts with expired lease", exhausted, maxAttempts);
        }
        List<UUID> ids = jobRepository.claimCandidateIds(batchSize, now, maxAttempts);
        if (ids.isEmpty()) {
            return List.of();
        }
        jobRepository.markRunning(ids, now, now.plusSeconds(properties.worker().leaseSeconds()));
        return jobRepository.findAllById(ids).stream()
                .map(job -> new JobClaim(job.getId(), job.getAttempts()))
                .toList();
    }
}

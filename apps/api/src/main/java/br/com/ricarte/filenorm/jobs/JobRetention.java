package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.JobRepository;
import br.com.ricarte.filenorm.storage.BlobStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Remove jobs finalizados há mais de {@code filenorm.jobs.retention-days} dias (0 = desligado). */
@Component
public class JobRetention {

    private static final Logger log = LoggerFactory.getLogger(JobRetention.class);
    private static final int BATCH = 200;

    /** Margem para não apagar o arquivo de um upload cuja transação ainda não confirmou. */
    static final Duration ORPHAN_GRACE = Duration.ofHours(1);

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final BlobStore blobStore;
    private final FilenormProperties properties;

    public JobRetention(JobRepository jobRepository, JobService jobService, BlobStore blobStore, FilenormProperties properties) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.blobStore = blobStore;
        this.properties = properties;
    }

    /**
     * Arquivos sem job correspondente (ex.: processo morto entre gravar o upload e confirmar o job,
     * ou exclusão cujo arquivo não pôde ser apagado). Caminho: {accountId}/{jobId}/{nome}.
     */
    @Scheduled(fixedDelayString = "${filenorm.jobs.retention-interval-ms:3600000}", initialDelay = 120_000)
    public void purgeOrphanBlobs() {
        if (!properties.worker().enabled()) {
            return;
        }
        removeOrphanBlobs(Instant.now().minus(ORPHAN_GRACE));
    }

    int removeOrphanBlobs(Instant cutoff) {
        int removed = 0;
        for (String path : blobStore.listOlderThan(cutoff)) {
            String[] parts = path.split("/");
            UUID jobId = parts.length >= 3 ? parseUuid(parts[1]) : null;
            if (jobId == null || !jobRepository.existsById(jobId)) {
                blobStore.delete(path);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("removed {} orphan upload file(s)", removed);
        }
        return removed;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Scheduled(fixedDelayString = "${filenorm.jobs.retention-interval-ms:3600000}", initialDelay = 60_000)
    public void purgeExpired() {
        int days = properties.jobs().retentionDays();
        if (days <= 0 || !properties.worker().enabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        List<UUID> ids = jobRepository.findFinishedBefore(cutoff, BATCH);
        for (UUID id : ids) {
            jobService.purgeById(id);
        }
        if (!ids.isEmpty()) {
            log.info("retention removed {} job(s) finished before {}", ids.size(), cutoff);
        }
    }
}

package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.config.FilenormProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobClaimer jobClaimer;
    private final JobService jobService;
    private final FilenormProperties properties;

    public JobWorker(JobClaimer jobClaimer, JobService jobService, FilenormProperties properties) {
        this.jobClaimer = jobClaimer;
        this.jobService = jobService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${filenorm.worker.poll-interval-ms:1000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }
        List<JobClaim> claims = jobClaimer.claim(properties.worker().batchSize());
        for (JobClaim claim : claims) {
            try {
                jobService.process(claim);
            } catch (RuntimeException ex) {
                // O lease expira e o job volta a ser elegível; um erro não pode abandonar o resto do lote.
                log.error("job {} attempt {} aborted", claim.jobId(), claim.attempt(), ex);
            }
        }
    }
}

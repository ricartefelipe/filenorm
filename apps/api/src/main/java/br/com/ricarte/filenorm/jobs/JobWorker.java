package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.parse.ParserRouter;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

    private final JobClaimer jobClaimer;
    private final JobService jobService;
    private final ParserRouter parserRouter;
    private final FilenormProperties properties;

    public JobWorker(
            JobClaimer jobClaimer,
            JobService jobService,
            ParserRouter parserRouter,
            FilenormProperties properties
    ) {
        this.jobClaimer = jobClaimer;
        this.jobService = jobService;
        this.parserRouter = parserRouter;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${filenorm.worker.poll-interval-ms:1000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }
        List<UUID> ids = jobClaimer.claim(properties.worker().batchSize());
        for (UUID id : ids) {
            jobService.processJob(id, parserRouter);
        }
    }
}

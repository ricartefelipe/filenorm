package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.domain.JobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Profundidade da fila exposta em /actuator/prometheus. */
@Component
public class JobMetrics {

    public JobMetrics(MeterRegistry registry, JobRepository jobRepository) {
        for (String status : new String[]{"queued", "running"}) {
            Gauge.builder("filenorm.jobs.current", jobRepository, repo -> repo.countByStatus(status))
                    .tag("status", status)
                    .register(registry);
        }
    }
}

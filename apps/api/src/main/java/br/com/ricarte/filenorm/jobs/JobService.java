package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.billing.CreditsService;
import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.Job;
import br.com.ricarte.filenorm.domain.JobRepository;
import br.com.ricarte.filenorm.domain.NormalizedEvent;
import br.com.ricarte.filenorm.domain.NormalizedEventRepository;
import br.com.ricarte.filenorm.parse.FormatDetector;
import br.com.ricarte.filenorm.parse.NormalizedEventData;
import br.com.ricarte.filenorm.parse.ParseException;
import br.com.ricarte.filenorm.parse.ParseResult;
import br.com.ricarte.filenorm.parse.ParserRouter;
import br.com.ricarte.filenorm.storage.BlobStore;
import br.com.ricarte.filenorm.web.AccountContext;
import br.com.ricarte.filenorm.web.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final NormalizedEventRepository normalizedEventRepository;
    private final BlobStore blobStore;
    private final CreditsService creditsService;
    private final FilenormProperties properties;

    public JobService(
            JobRepository jobRepository,
            NormalizedEventRepository normalizedEventRepository,
            BlobStore blobStore,
            CreditsService creditsService,
            FilenormProperties properties
    ) {
        this.jobRepository = jobRepository;
        this.normalizedEventRepository = normalizedEventRepository;
        this.blobStore = blobStore;
        this.creditsService = creditsService;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Object> createJob(MultipartFile file, String format, String preset) {
        UUID accountId = AccountContext.requireAccountId();
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file_required");
        }
        if (file.getSize() > properties.jobs().maxUploadBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file_too_large");
        }

        String resolvedFormat = format == null || format.isBlank() ? "auto" : format.trim().toLowerCase();
        byte[] preview;
        try {
            preview = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file_read_failed");
        }
        if (!"auto".equals(resolvedFormat)) {
            resolvedFormat = resolvedFormat;
        } else {
            resolvedFormat = FormatDetector.detect(preview, file.getOriginalFilename(), file.getContentType());
        }

        long estimatedCost = estimateCost(resolvedFormat, preview);
        creditsService.requireAvailable(accountId, estimatedCost);

        UUID jobId = UUID.randomUUID();
        String blobPath = accountId + "/" + jobId + "/" + sanitizeFilename(file.getOriginalFilename());
        blobStore.save(blobPath, new ByteArrayInputStream(preview), preview.length);

        Instant now = Instant.now();
        Job job = new Job(
                jobId,
                accountId,
                "queued",
                resolvedFormat,
                preset,
                file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                blobPath,
                now
        );
        jobRepository.save(job);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId().toString());
        body.put("status", job.getStatus());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID jobId) {
        Job job = loadOwnedJob(jobId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId().toString());
        body.put("status", job.getStatus());
        body.put("format", job.getFormat());
        body.put("preset", job.getPreset());
        body.put("originalFilename", job.getOriginalFilename());
        body.put("byteSize", job.getByteSize());
        body.put("pages", job.getPages());
        body.put("creditsCharged", job.getCreditsCharged());
        body.put("errorCode", job.getErrorCode());
        body.put("errorMessage", job.getErrorMessage());
        body.put("createdAt", job.getCreatedAt().toString());
        body.put("startedAt", job.getStartedAt() == null ? null : job.getStartedAt().toString());
        body.put("finishedAt", job.getFinishedAt() == null ? null : job.getFinishedAt().toString());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEvents(UUID jobId) {
        Job job = loadOwnedJob(jobId);
        List<NormalizedEvent> events = normalizedEventRepository.findByJobIdOrderByOrdinalAsc(job.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId().toString());
        body.put("status", job.getStatus());
        body.put("events", events.stream().map(this::toEventMap).toList());
        return body;
    }

    @Transactional
    public void processJob(UUID jobId, ParserRouter parserRouter) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !"running".equals(job.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        try (InputStream input = blobStore.open(job.getBlobPath())) {
            byte[] content = input.readAllBytes();
            ParseResult result = parserRouter.parse(
                    content,
                    job.getFormat(),
                    job.getPreset(),
                    job.getOriginalFilename(),
                    job.getContentType()
            );
            saveEvents(job.getId(), result.events());
            int cost = computeCost(job.getFormat(), result.pages());
            creditsService.charge(job.getAccountId(), cost, "job_" + job.getFormat(), job.getId());
            job.setPages(result.pages());
            job.setCreditsCharged(cost);
            job.setStatus("succeeded");
            job.setFinishedAt(now);
            jobRepository.save(job);
        } catch (ParseException ex) {
            failJob(job, ex.getCode(), ex.getMessage(), now);
        } catch (Exception ex) {
            failJob(job, "processing_failed", ex.getMessage(), now);
        }
    }

    private void failJob(Job job, String code, String message, Instant now) {
        job.setStatus("failed");
        job.setErrorCode(code);
        job.setErrorMessage(message == null ? code : message);
        job.setCreditsCharged(0);
        job.setFinishedAt(now);
        jobRepository.save(job);
    }

    private void saveEvents(UUID jobId, List<NormalizedEventData> events) {
        int ordinal = 0;
        for (NormalizedEventData data : events) {
            normalizedEventRepository.save(new NormalizedEvent(
                    UUID.randomUUID(),
                    jobId,
                    data.externalId(),
                    data.postedAt(),
                    data.amount(),
                    data.currency(),
                    data.direction(),
                    data.counterparty(),
                    data.description(),
                    data.rawRef(),
                    data.confidence(),
                    ordinal++
            ));
        }
    }

    public int computeCost(String format, Integer pages) {
        return switch (format) {
            case "pdf" -> {
                int pageCount = pages == null ? 1 : pages;
                int perPage = properties.credits().pdfCostPerPage();
                int min = properties.credits().pdfMinCost();
                yield Math.max(min, pageCount * perPage);
            }
            case "ofx" -> properties.credits().ofxCost();
            case "cnab240" -> properties.credits().cnabCost();
            default -> properties.credits().csvCost();
        };
    }

    private long estimateCost(String format, byte[] preview) {
        if ("pdf".equals(format)) {
            return properties.credits().pdfMinCost();
        }
        return 1;
    }

    private Job loadOwnedJob(UUID jobId) {
        UUID accountId = AccountContext.requireAccountId();
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
        if (!job.getAccountId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "job_not_found");
        }
        return job;
    }

    private Map<String, Object> toEventMap(NormalizedEvent event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("externalId", event.getExternalId());
        item.put("postedAt", event.getPostedAt().toString());
        item.put("amount", event.getAmount().toPlainString());
        item.put("currency", event.getCurrency());
        item.put("direction", event.getDirection());
        item.put("counterparty", event.getCounterparty());
        item.put("description", event.getDescription());
        item.put("rawRef", event.getRawRef());
        item.put("confidence", event.getConfidence());
        item.put("ordinal", event.getOrdinal());
        return item;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

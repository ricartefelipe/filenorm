package br.com.ricarte.filenorm.jobs;

import br.com.ricarte.filenorm.billing.CreditsService;
import br.com.ricarte.filenorm.billing.CreditsService.ChargeResult;
import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.Job;
import br.com.ricarte.filenorm.domain.JobIssue;
import br.com.ricarte.filenorm.domain.JobIssueRepository;
import br.com.ricarte.filenorm.domain.JobRepository;
import br.com.ricarte.filenorm.domain.NormalizedEvent;
import br.com.ricarte.filenorm.domain.NormalizedEventRepository;
import br.com.ricarte.filenorm.parse.BalanceCheck;
import br.com.ricarte.filenorm.parse.CsvParser;
import br.com.ricarte.filenorm.parse.FormatDetector;
import br.com.ricarte.filenorm.parse.NormalizedEventData;
import br.com.ricarte.filenorm.parse.ParseException;
import br.com.ricarte.filenorm.parse.ParseIssue;
import br.com.ricarte.filenorm.parse.ParseResult;
import br.com.ricarte.filenorm.parse.ParserRouter;
import br.com.ricarte.filenorm.storage.BlobStore;
import br.com.ricarte.filenorm.web.AccountContext;
import br.com.ricarte.filenorm.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    /** Limite de issues persistidas por job; a contagem total fica no job. */
    static final int MAX_STORED_ISSUES = 1000;

    private final JobRepository jobRepository;
    private final NormalizedEventRepository normalizedEventRepository;
    private final JobIssueRepository jobIssueRepository;
    private final BlobStore blobStore;
    private final CreditsService creditsService;
    private final ParserRouter parserRouter;
    private final FilenormProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public JobService(
            JobRepository jobRepository,
            NormalizedEventRepository normalizedEventRepository,
            JobIssueRepository jobIssueRepository,
            BlobStore blobStore,
            CreditsService creditsService,
            ParserRouter parserRouter,
            FilenormProperties properties,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.normalizedEventRepository = normalizedEventRepository;
        this.jobIssueRepository = jobIssueRepository;
        this.blobStore = blobStore;
        this.creditsService = creditsService;
        this.parserRouter = parserRouter;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
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
        String requestedFormat = format == null || format.isBlank() ? "auto" : format.trim().toLowerCase(Locale.ROOT);
        if (!"auto".equals(requestedFormat) && !FormatDetector.FORMATS.contains(requestedFormat)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_format");
        }
        String normalizedPreset = preset == null || preset.isBlank() ? null : preset.trim().toLowerCase(Locale.ROOT);
        if (normalizedPreset != null && !CsvParser.PRESETS.contains(normalizedPreset)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_preset");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file_read_failed");
        }
        String resolvedFormat = "auto".equals(requestedFormat)
                ? FormatDetector.detect(content, file.getOriginalFilename(), file.getContentType())
                : requestedFormat;
        if ("pdf".equals(resolvedFormat) && !FormatDetector.isPdf(content)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file_not_pdf");
        }

        creditsService.requireAvailable(accountId, estimateCost(resolvedFormat));

        UUID jobId = UUID.randomUUID();
        String blobPath = accountId + "/" + jobId + "/" + sanitizeFilename(file.getOriginalFilename());
        try {
            blobStore.save(blobPath, new ByteArrayInputStream(content), content.length);
        } catch (IllegalStateException ex) {
            log.error("could not store upload for account {}", accountId, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable");
        }
        deleteBlobIfRolledBack(blobPath);

        Job job = new Job(
                jobId,
                accountId,
                "queued",
                resolvedFormat,
                normalizedPreset,
                file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                blobPath,
                Instant.now()
        );
        jobRepository.save(job);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId().toString());
        body.put("status", job.getStatus());
        body.put("format", job.getFormat());
        body.put("estimatedCredits", estimateCost(resolvedFormat));
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
        body.put("attempts", job.getAttempts());
        body.put("eventCount", job.getEventCount());
        body.put("rejectedCount", job.getRejectedCount());
        body.put("skippedCount", job.getSkippedCount());
        body.put("warningCount", job.getWarningCount());
        body.put("balanceCheck", balanceCheck(job));
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

    @Transactional(readOnly = true)
    public Map<String, Object> getIssues(UUID jobId) {
        Job job = loadOwnedJob(jobId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId().toString());
        body.put("status", job.getStatus());
        body.put("rejectedCount", job.getRejectedCount());
        body.put("skippedCount", job.getSkippedCount());
        body.put("warningCount", job.getWarningCount());
        body.put("issues", jobIssueRepository.findByJobIdOrderByOrdinalAsc(job.getId()).stream().map(issue -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("severity", issue.getSeverity());
            item.put("code", issue.getCode());
            item.put("record", issue.getRecordRef());
            item.put("detail", issue.getDetail());
            return item;
        }).toList());
        return body;
    }

    /** Exclusão integral: eventos, issues, arquivo original e o job. O ledger de créditos é mantido para auditoria. */
    @Transactional
    public void deleteJob(UUID jobId) {
        Job job = loadOwnedJob(jobId);
        if ("running".equals(job.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "job_running");
        }
        purge(job);
    }

    @Transactional
    public void purgeById(UUID jobId) {
        jobRepository.findForUpdate(jobId)
                .filter(job -> !"running".equals(job.getStatus()) && !"queued".equals(job.getStatus()))
                .ifPresent(this::purge);
    }

    private void purge(Job job) {
        normalizedEventRepository.deleteByJobId(job.getId());
        jobIssueRepository.deleteByJobId(job.getId());
        jobRepository.deleteById(job.getId());
        String blobPath = job.getBlobPath();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                blobStore.delete(blobPath);
            }
        });
    }

    /**
     * Processa uma tentativa. O parse roda fora de transação; o resultado (eventos, issues,
     * cobrança e status) é gravado numa única transação, condicionada a esta tentativa ainda
     * ser a dona do job. Uma tentativa cujo lease expirou e foi assumida por outra não grava nada.
     */
    public void process(JobClaim claim) {
        Job job = jobRepository.findById(claim.jobId()).orElse(null);
        if (job == null || !isCurrent(job, claim)) {
            return;
        }
        ParseResult result;
        try (InputStream input = blobStore.open(job.getBlobPath())) {
            byte[] content = input.readAllBytes();
            result = parserRouter.parse(content, job.getFormat(), job.getPreset(), job.getOriginalFilename(), job.getContentType());
        } catch (ParseException ex) {
            fail(claim, ex.getCode(), ex.getCode());
            return;
        } catch (IllegalStateException | IOException ex) {
            fail(claim, "blob_unavailable", "original file could not be read");
            return;
        } catch (RuntimeException ex) {
            log.error("job {} parser error", claim.jobId(), ex);
            fail(claim, "processing_failed", ex.getClass().getSimpleName());
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> complete(claim, result));
        } catch (InsufficientCredits ex) {
            fail(claim, "insufficient_credits", "not enough credits to charge this job");
        } catch (RuntimeException ex) {
            log.error("job {} could not persist results", claim.jobId(), ex);
            fail(claim, "persist_failed", ex.getClass().getSimpleName());
        }
    }

    private void complete(JobClaim claim, ParseResult result) {
        Job job = jobRepository.findForUpdate(claim.jobId()).orElse(null);
        if (job == null || !isCurrent(job, claim)) {
            return;
        }
        BalanceCheck balance = BalanceCheck.verify(result.balances(), result.events());
        List<ParseIssue> issues = new ArrayList<>(result.issues());
        if (BalanceCheck.MISMATCH.equals(balance.status())) {
            // Saldo que não fecha nunca resulta em "succeeded": falta ou sobra movimento no resultado.
            issues.add(ParseIssue.rejected("balance_mismatch", "statement",
                    "saldo final declarado " + balance.closing().toPlainString()
                            + ", calculado " + balance.computedClosing().toPlainString()
                            + ", diferença " + balance.difference().toPlainString()));
        }
        normalizedEventRepository.deleteByJobId(job.getId());
        jobIssueRepository.deleteByJobId(job.getId());
        saveEvents(job, result.events());
        saveIssues(job.getId(), issues);

        int cost = computeCost(job.getFormat(), result.pages());
        ChargeResult charge = creditsService.charge(job.getAccountId(), cost, "job_" + job.getFormat(), job.getId(), "job:" + job.getId());
        if (charge == ChargeResult.INSUFFICIENT) {
            // Desfaz eventos e issues desta transação; a falha é gravada em outra transação.
            throw new InsufficientCredits();
        }
        int rejected = (int) count(issues, ParseIssue.REJECTED);
        job = jobRepository.findById(claim.jobId()).orElseThrow();
        job.setBalanceCheck(balance.status(), balance.source(), balance.reason(), balance.opening(),
                balance.closing(), balance.computedClosing(), balance.difference());
        job.setPages(result.pages());
        job.setCreditsCharged(cost);
        job.setCounts(result.events().size(), rejected, (int) count(issues, ParseIssue.SKIPPED), (int) count(issues, ParseIssue.WARNING));
        job.setStatus(rejected > 0 ? "partial" : "succeeded");
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        job.setLeaseExpiresAt(null);
        jobRepository.save(job);
        record(job.getStatus(), job.getFormat());
        log.info("job {} {} format={} events={} rejected={} balance={} credits={}",
                job.getId(), job.getStatus(), job.getFormat(), job.getEventCount(), rejected, balance.status(), cost);
        meterRegistry.counter("filenorm.jobs.balance", "status", balance.status(), "format", job.getFormat()).increment();
    }

    private static final class InsufficientCredits extends RuntimeException {
        private InsufficientCredits() {
            super("insufficient_credits", null, false, false);
        }
    }

    private void fail(JobClaim claim, String code, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobRepository.findForUpdate(claim.jobId()).orElse(null);
            if (job == null || !isCurrent(job, claim)) {
                return;
            }
            job.setStatus("failed");
            job.setErrorCode(code);
            job.setErrorMessage(message == null ? code : message);
            job.setCreditsCharged(0);
            job.setFinishedAt(Instant.now());
            job.setLeaseExpiresAt(null);
            jobRepository.save(job);
            record("failed", job.getFormat());
            log.info("job {} failed code={}", job.getId(), code);
        });
    }

    private static long count(List<ParseIssue> issues, String severity) {
        return issues.stream().filter(issue -> issue.severity().equals(severity)).count();
    }

    private static boolean isCurrent(Job job, JobClaim claim) {
        return "running".equals(job.getStatus()) && job.getAttempts() == claim.attempt();
    }

    private void record(String status, String format) {
        meterRegistry.counter("filenorm.jobs.completed", "status", status, "format", format).increment();
    }

    private void saveEvents(Job job, List<NormalizedEventData> events) {
        List<String> keys = EventKeys.assign(job.getFormat(), events);
        List<NormalizedEvent> rows = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            NormalizedEventData data = events.get(i);
            rows.add(new NormalizedEvent(
                    UUID.randomUUID(),
                    job.getId(),
                    truncate(data.externalId(), 500),
                    data.postedAt(),
                    data.amount(),
                    data.currency(),
                    data.direction(),
                    truncate(data.counterparty(), 500),
                    data.description() == null ? "" : data.description(),
                    data.rawRef(),
                    data.confidence(),
                    i,
                    keys.get(i)
            ));
        }
        normalizedEventRepository.saveAll(rows);
    }

    private void saveIssues(UUID jobId, List<ParseIssue> issues) {
        List<JobIssue> rows = new ArrayList<>();
        for (int i = 0; i < issues.size() && i < MAX_STORED_ISSUES; i++) {
            ParseIssue issue = issues.get(i);
            rows.add(new JobIssue(UUID.randomUUID(), jobId, issue.severity(), issue.code(),
                    truncate(issue.recordRef(), 80), truncate(issue.detail(), 500), i));
        }
        jobIssueRepository.saveAll(rows);
        issues.stream().map(ParseIssue::severity).forEach(severity ->
                meterRegistry.counter("filenorm.jobs.issues", "severity", severity).increment());
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

    private long estimateCost(String format) {
        return computeCost(format, null);
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

    static Map<String, Object> balanceCheck(Job job) {
        if (job.getBalanceStatus() == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", job.getBalanceStatus());
        item.put("source", job.getBalanceSource());
        item.put("reason", job.getBalanceReason());
        item.put("openingBalance", plain(job.getOpeningBalance()));
        item.put("closingBalance", plain(job.getClosingBalance()));
        item.put("computedClosingBalance", plain(job.getComputedClosingBalance()));
        item.put("difference", plain(job.getBalanceDifference()));
        return item;
    }

    private static String plain(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private Map<String, Object> toEventMap(NormalizedEvent event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("externalId", event.getExternalId());
        item.put("idempotencyKey", event.getIdempotencyKey());
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

    private void deleteBlobIfRolledBack(String blobPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    blobStore.delete(blobPath);
                }
            }
        });
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("^\\.+", "_");
        return sanitized.length() > 120 ? sanitized.substring(sanitized.length() - 120) : sanitized;
    }
}

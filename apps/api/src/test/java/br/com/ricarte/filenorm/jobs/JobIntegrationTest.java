package br.com.ricarte.filenorm.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.Job;
import br.com.ricarte.filenorm.domain.JobRepository;
import br.com.ricarte.filenorm.support.DatabaseCleaner;
import br.com.ricarte.filenorm.support.NoOpMailConfig;
import br.com.ricarte.filenorm.support.PostgresIntegrationTest;
import br.com.ricarte.filenorm.support.TestAccounts;
import br.com.ricarte.filenorm.support.TestAccounts.TestAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Import(NoOpMailConfig.class)
class JobIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private JobClaimer jobClaimer;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FilenormProperties properties;

    private TestAccount account;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        account = TestAccounts.create(mockMvc, objectMapper);
    }

    @Test
    void uploadsOfxAndNormalizesEventsWithStableIdempotencyKeys() throws Exception {
        String first = upload(account, "sample.ofx", "ofx", null);
        processAll();
        String second = upload(account, "sample.ofx", "auto", null);
        processAll();

        mockMvc.perform(get("/v1/jobs/" + first).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.creditsCharged").value(1))
                .andExpect(jsonPath("$.eventCount").value(2))
                .andExpect(jsonPath("$.attempts").value(1));

        JsonNode a = events(account, first);
        JsonNode b = events(account, second);
        assertThat(a).hasSize(2);
        assertThat(a.get(0).get("amount").asText()).isEqualTo("42.50");
        assertThat(a.get(0).get("idempotencyKey").asText()).hasSize(64)
                .isEqualTo(b.get(0).get("idempotencyKey").asText());
        assertThat(balance()).isEqualTo(properties.credits().signupGrant() - 2);
    }

    @Test
    void csvWithInvalidRowIsPartialAndExplainsRejectedLines() throws Exception {
        String jobId = upload(account, "nubank-conta.csv", "csv", "nubank");
        processAll();

        mockMvc.perform(get("/v1/jobs/" + jobId).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(jsonPath("$.status").value("partial"))
                .andExpect(jsonPath("$.eventCount").value(3))
                .andExpect(jsonPath("$.rejectedCount").value(1));
        mockMvc.perform(get("/v1/jobs/" + jobId + "/issues").header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[0].severity").value("rejected"))
                .andExpect(jsonPath("$.issues[0].code").value("csv_amount_invalid"))
                .andExpect(jsonPath("$.issues[0].record").value("line 4"));
    }

    @Test
    void cnabReturnFileIsDetectedAndNormalized() throws Exception {
        String jobId = upload(account, "cnab240-retorno-cobranca.ret", "auto", null);
        processAll();

        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getFormat()).isEqualTo("cnab240");
        assertThat(job.getStatus()).isEqualTo("succeeded");
        assertThat(job.getEventCount()).isEqualTo(2);
        assertThat(job.getSkippedCount()).isEqualTo(1);
    }

    @Test
    void statementWithBalancesIsVerified() throws Exception {
        String jobId = upload(account, "extrato-com-saldo.csv", "auto", null);
        processAll();

        mockMvc.perform(get("/v1/jobs/" + jobId).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.balanceCheck.status").value("verified"))
                .andExpect(jsonPath("$.balanceCheck.openingBalance").value("1000.00"))
                .andExpect(jsonPath("$.balanceCheck.closingBalance").value("2000.00"))
                .andExpect(jsonPath("$.balanceCheck.difference").value("0.00"));
    }

    @Test
    void statementThatDoesNotCloseIsPartialWithTheDifference() throws Exception {
        String csv = "Data;Descrição;Valor;Saldo\n01/08/2025;SALDO ANTERIOR;;1.000,00\n"
                + "02/08/2025;PIX;1.500,00;2.500,00\n05/08/2025;BOLETO;-470,10;2.000,00\n";
        String jobId = create(account, new MockMultipartFile("file", "extrato.csv", "text/csv", csv.getBytes()), "csv", null);
        processAll();

        mockMvc.perform(get("/v1/jobs/" + jobId).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(jsonPath("$.status").value("partial"))
                .andExpect(jsonPath("$.balanceCheck.status").value("mismatch"))
                .andExpect(jsonPath("$.balanceCheck.difference").value("-29.90"));
        mockMvc.perform(get("/v1/jobs/" + jobId + "/issues").header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(jsonPath("$.issues[?(@.code == 'csv_balance_break')].record").value("line 4"))
                .andExpect(jsonPath("$.issues[?(@.code == 'balance_mismatch')].severity").value("rejected"));
    }

    @Test
    void ofxReportsBalanceCheckAsUnavailable() throws Exception {
        String jobId = upload(account, "sample.ofx", "ofx", null);
        processAll();

        mockMvc.perform(get("/v1/jobs/" + jobId).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.balanceCheck.status").value("unavailable"))
                .andExpect(jsonPath("$.balanceCheck.reason").value("balances_not_found"));
    }

    @Test
    void parseFailureIsVisibleAndNotCharged() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.csv", "text/csv", "foo,bar\n1,2\n".getBytes());
        String jobId = create(account, file, "csv", null);
        processAll();

        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo("failed");
        assertThat(job.getErrorCode()).isEqualTo("csv_columns_not_found");
        assertThat(balance()).isEqualTo(properties.credits().signupGrant());
    }

    @Test
    void concurrentJobsCannotOverspendTheLastCredit() throws Exception {
        String a = upload(account, "sample.ofx", "ofx", null);
        String b = upload(account, "sample.ofx", "ofx", null);
        jdbc.update("UPDATE accounts SET credits_balance = 1 WHERE id = ?", account.accountId());
        List<JobClaim> claims = jobClaimer.claim(10);
        assertThat(claims).hasSize(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (JobClaim claim : claims) {
            futures.add(pool.submit(() -> {
                start.await();
                jobService.process(claim);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<String> statuses = List.of(jobStatus(a), jobStatus(b));
        assertThat(statuses).containsExactlyInAnyOrder("succeeded", "failed");
        assertThat(balance()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE job_id IS NOT NULL", Long.class)).isEqualTo(1);
        String failed = "failed".equals(jobStatus(a)) ? a : b;
        Job failedJob = jobRepository.findById(UUID.fromString(failed)).orElseThrow();
        assertThat(failedJob.getErrorCode()).isEqualTo("insufficient_credits");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM normalized_events WHERE job_id = ?", Long.class, failedJob.getId())).isZero();
    }

    @Test
    void expiredLeaseIsReclaimedAndTheStaleAttemptCannotCommit() throws Exception {
        String jobId = upload(account, "sample.ofx", "ofx", null);
        JobClaim first = jobClaimer.claim(1).get(0);
        assertThat(jobClaimer.claim(1)).as("lease ainda válido").isEmpty();

        // Simula worker morto (kill -9) depois do claim: o lease expira.
        jdbc.update("UPDATE jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ?", first.jobId());
        JobClaim second = jobClaimer.claim(1).get(0);
        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.attempt()).isEqualTo(2);

        jobService.process(first);
        assertThat(jobStatus(jobId)).as("tentativa antiga não grava").isEqualTo("running");

        jobService.process(second);
        jobService.process(second);
        assertThat(jobStatus(jobId)).isEqualTo("succeeded");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM normalized_events WHERE job_id = ?", Long.class, second.jobId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE job_id = ?", Long.class, second.jobId())).isEqualTo(1);
        assertThat(balance()).isEqualTo(properties.credits().signupGrant() - 1);
    }

    @Test
    void jobsThatKeepDyingFailAfterMaxAttempts() throws Exception {
        String jobId = upload(account, "sample.ofx", "ofx", null);
        jdbc.update("UPDATE jobs SET status = 'running', attempts = ?, lease_expires_at = now() - interval '1 second' WHERE id = ?::uuid",
                properties.worker().maxAttempts(), jobId);

        assertThat(jobClaimer.claim(5)).isEmpty();
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo("failed");
        assertThat(job.getErrorCode()).isEqualTo("max_attempts_exceeded");
    }

    @Test
    void legacyRunningJobWithoutLeaseIsRecovered() throws Exception {
        String jobId = upload(account, "sample.ofx", "ofx", null);
        jdbc.update("UPDATE jobs SET status = 'running', lease_expires_at = NULL WHERE id = ?::uuid", jobId);

        processAll();

        assertThat(jobStatus(jobId)).isEqualTo("succeeded");
    }

    @Test
    void otherAccountsCannotSeeOrDeleteJobs() throws Exception {
        String jobId = upload(account, "sample.ofx", "ofx", null);
        processAll();
        TestAccount other = TestAccounts.create(mockMvc, objectMapper);
        String bearer = "Bearer " + other.apiKey();

        mockMvc.perform(get("/v1/jobs/" + jobId).header("Authorization", bearer)).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/jobs/" + jobId + "/events").header("Authorization", bearer)).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/jobs/" + jobId + "/issues").header("Authorization", bearer)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/jobs/" + jobId).header("Authorization", bearer)).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/jobs/" + jobId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/jobs/" + jobId).header("Authorization", "Bearer " + account.sessionToken()))
                .andExpect(status().isUnauthorized());
        assertThat(jobStatus(jobId)).isEqualTo("succeeded");
    }

    @Test
    void deleteRemovesEventsIssuesAndOriginalFile() throws Exception {
        String jobId = upload(account, "nubank-conta.csv", "csv", "nubank");
        processAll();
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        Path blob = Path.of(properties.storage().path()).toAbsolutePath().resolve(job.getBlobPath());
        assertThat(blob).exists();

        mockMvc.perform(delete("/v1/jobs/" + jobId).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isOk());

        assertThat(jobRepository.findById(job.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM normalized_events WHERE job_id = ?", Long.class, job.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_issues WHERE job_id = ?", Long.class, job.getId())).isZero();
        assertThat(Files.exists(blob)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE job_id = ?", Long.class, job.getId()))
                .as("ledger mantido para auditoria").isEqualTo(1);
    }

    @Test
    void rejectsInvalidUploadParameters() throws Exception {
        MockMultipartFile text = new MockMultipartFile("file", "x.pdf", "application/pdf", "not a pdf".getBytes());
        String bearer = "Bearer " + account.apiKey();

        mockMvc.perform(multipart("/v1/jobs").file(text).param("format", "xls").header("Authorization", bearer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_format"));
        mockMvc.perform(multipart("/v1/jobs").file(text).param("format", "pdf").header("Authorization", bearer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("file_not_pdf"));
        mockMvc.perform(multipart("/v1/jobs").file(text).param("format", "csv").param("preset", "foo").header("Authorization", bearer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unknown_preset"));
        assertThat(jobRepository.count()).isZero();
    }

    private String upload(TestAccount owner, String fixture, String format, String preset) throws Exception {
        byte[] content = new ClassPathResource("fixtures/" + fixture).getContentAsByteArray();
        return create(owner, new MockMultipartFile("file", fixture, "application/octet-stream", content), format, preset);
    }

    private String create(TestAccount owner, MockMultipartFile file, String format, String preset) throws Exception {
        var request = multipart("/v1/jobs").file(file).param("format", format)
                .header("Authorization", "Bearer " + owner.apiKey());
        if (preset != null) {
            request = request.param("preset", preset);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText();
    }

    private void processAll() {
        List<JobClaim> claims;
        while (!(claims = jobClaimer.claim(10)).isEmpty()) {
            claims.forEach(jobService::process);
        }
    }

    private JsonNode events(TestAccount owner, String jobId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/jobs/" + jobId + "/events")
                        .header("Authorization", "Bearer " + owner.apiKey()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("events");
    }

    private String jobStatus(String jobId) {
        return jdbc.queryForObject("SELECT status FROM jobs WHERE id = ?::uuid", String.class, jobId);
    }

    private long balance() {
        return jdbc.queryForObject("SELECT credits_balance FROM accounts WHERE id = ?", Long.class, account.accountId());
    }
}

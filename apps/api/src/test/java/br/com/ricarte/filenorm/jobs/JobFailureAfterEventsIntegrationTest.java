package br.com.ricarte.filenorm.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.filenorm.billing.CreditsService;
import br.com.ricarte.filenorm.domain.Job;
import br.com.ricarte.filenorm.domain.JobRepository;
import br.com.ricarte.filenorm.support.DatabaseCleaner;
import br.com.ricarte.filenorm.support.NoOpMailConfig;
import br.com.ricarte.filenorm.support.PostgresIntegrationTest;
import br.com.ricarte.filenorm.support.TestAccounts;
import br.com.ricarte.filenorm.support.TestAccounts.TestAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Falha depois de gravar os eventos (na cobrança): nada fica pela metade e a falha é visível. */
@AutoConfigureMockMvc
@Import(NoOpMailConfig.class)
class JobFailureAfterEventsIntegrationTest extends PostgresIntegrationTest {

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

    @SpyBean
    private CreditsService creditsService;

    @Test
    void failureAfterSavingEventsRollsBackEventsAndCharge() throws Exception {
        databaseCleaner.clean();
        TestAccount account = TestAccounts.create(mockMvc, objectMapper);
        long before = balance(account);
        byte[] ofx = new ClassPathResource("fixtures/sample.ofx").getContentAsByteArray();
        mockMvc.perform(multipart("/v1/jobs")
                        .file(new MockMultipartFile("file", "sample.ofx", "application/x-ofx", ofx))
                        .header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isAccepted());
        doThrow(new DataAccessResourceFailureException("connection reset"))
                .when(creditsService).charge(any(UUID.class), anyLong(), anyString(), any(UUID.class), anyString());

        JobClaim claim = jobClaimer.claim(1).get(0);
        jobService.process(claim);

        Job job = jobRepository.findById(claim.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo("failed");
        assertThat(job.getErrorCode()).isEqualTo("persist_failed");
        assertThat(job.getCreditsCharged()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM normalized_events", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE job_id IS NOT NULL", Long.class)).isZero();
        assertThat(balance(account)).isEqualTo(before);
    }

    private long balance(TestAccount account) {
        return jdbc.queryForObject("SELECT credits_balance FROM accounts WHERE id = ?", Long.class, account.accountId());
    }
}

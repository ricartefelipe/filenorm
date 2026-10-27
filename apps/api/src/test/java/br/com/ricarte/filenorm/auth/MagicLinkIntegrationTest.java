package br.com.ricarte.filenorm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.filenorm.support.DatabaseCleaner;
import br.com.ricarte.filenorm.support.NoOpMailConfig;
import br.com.ricarte.filenorm.support.PostgresIntegrationTest;
import br.com.ricarte.filenorm.support.TestAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import(NoOpMailConfig.class)
class MagicLinkIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @Test
    void linkIsOnlyRevealedWithTheDemoAccessCode() throws Exception {
        mockMvc.perform(post("/v1/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.magicLink").doesNotExist());
        mockMvc.perform(post("/v1/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "127.0.0.1")
                        .content("{\"email\":\"a@example.com\",\"accessCode\":\"wrong\"}"))
                .andExpect(jsonPath("$.magicLink").doesNotExist());
        mockMvc.perform(post("/v1/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"accessCode\":\"" + TestAccounts.DEMO_CODE + "\"}"))
                .andExpect(jsonPath("$.magicLink").exists());
    }

    @Test
    void concurrentUseOfTheSameLinkCreatesExactlyOneSession() throws Exception {
        String body = mockMvc.perform(post("/v1/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@example.com\",\"accessCode\":\"" + TestAccounts.DEMO_CODE + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String link = objectMapper.readTree(body).get("magicLink").asText();
        String verify = "{\"token\":\"" + link.substring(link.indexOf("token=") + 6) + "\"}";

        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/v1/auth/verify").contentType(MediaType.APPLICATION_JSON).content(verify))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses).filteredOn(code -> code != 200).containsOnly(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_tokens", Long.class)).isZero();
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        String body = mockMvc.perform(post("/v1/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"c@example.com\",\"accessCode\":\"" + TestAccounts.DEMO_CODE + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String link = objectMapper.readTree(body).get("magicLink").asText();
        jdbc.update("UPDATE login_tokens SET expires_at = now() - interval '1 second'");

        mockMvc.perform(post("/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link.substring(link.indexOf("token=") + 6) + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions", Long.class)).isZero();
    }
}

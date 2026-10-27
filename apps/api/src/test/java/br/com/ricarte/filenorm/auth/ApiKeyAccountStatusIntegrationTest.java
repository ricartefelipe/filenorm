package br.com.ricarte.filenorm.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.filenorm.support.DatabaseCleaner;
import br.com.ricarte.filenorm.support.NoOpMailConfig;
import br.com.ricarte.filenorm.support.PostgresIntegrationTest;
import br.com.ricarte.filenorm.support.TestAccounts;
import br.com.ricarte.filenorm.support.TestAccounts.TestAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class ApiKeyAccountStatusIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private JdbcTemplate jdbc;

    private TestAccount account;
    private String unknownJob;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        account = TestAccounts.create(mockMvc, objectMapper);
        unknownJob = "/v1/jobs/" + UUID.randomUUID();
    }

    @Test
    void activeKeyIsAcceptedAndRevokedOrUnknownKeysAreNot() throws Exception {
        mockMvc.perform(get(unknownJob).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(unknownJob).header("Authorization", "Bearer fn_live_" + "0".repeat(48)))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE api_keys SET revoked_at = now() WHERE account_id = ?", account.accountId());
        mockMvc.perform(get(unknownJob).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingTheAccountRevokesApiKeyAccess() throws Exception {
        mockMvc.perform(post("/internal/v1/totalrecall/users")
                        .header("X-TotalRecall-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"action\":\"disable\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/totalrecall/users")
                        .header("X-TotalRecall-Token", "test-provision-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"action\":\"disable\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(unknownJob).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + account.sessionToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccountLosesApiKeyAccess() throws Exception {
        jdbc.update("UPDATE accounts SET expires_at = now() - interval '1 minute' WHERE id = ?", account.accountId());

        mockMvc.perform(get(unknownJob).header("Authorization", "Bearer " + account.apiKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightIsAnsweredOnlyForTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/v1/account/usage")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mockMvc.perform(options("/v1/jobs")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/v1/account/usage")).andExpect(status().isUnauthorized());
    }
}

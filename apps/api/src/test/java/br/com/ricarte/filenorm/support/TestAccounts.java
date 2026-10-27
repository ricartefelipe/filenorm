package br.com.ricarte.filenorm.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Cria contas reais pelo fluxo público (magic link → sessão → API key). */
public final class TestAccounts {

    /** Igual a filenorm.auth.demo-access-code em application-test.yml. */
    public static final String DEMO_CODE = "test-demo-code";

    private TestAccounts() {
    }

    public record TestAccount(UUID accountId, String email, String sessionToken, String apiKey) {
    }

    public static TestAccount create(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        String email = "acct-" + UUID.randomUUID() + "@example.com";
        MvcResult magic = mockMvc.perform(post("/v1/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"Test\",\"accessCode\":\"" + DEMO_CODE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String link = objectMapper.readTree(magic.getResponse().getContentAsString()).get("magicLink").asText();
        String token = link.substring(link.indexOf("token=") + 6);

        MvcResult verify = mockMvc.perform(post("/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(verify.getResponse().getContentAsString());
        String sessionToken = session.get("sessionToken").asText();

        MvcResult keyResult = mockMvc.perform(post("/v1/account/api-keys")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String apiKey = objectMapper.readTree(keyResult.getResponse().getContentAsString()).get("apiKey").asText();
        return new TestAccount(UUID.fromString(session.get("accountId").asText()), email, sessionToken, apiKey);
    }
}

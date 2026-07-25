package br.com.ricarte.filenorm.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.filenorm.domain.NormalizedEventRepository;
import br.com.ricarte.filenorm.parse.ParserRouter;
import br.com.ricarte.filenorm.support.DatabaseCleaner;
import br.com.ricarte.filenorm.support.NoOpMailConfig;
import br.com.ricarte.filenorm.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
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
    private ParserRouter parserRouter;

    @Autowired
    private NormalizedEventRepository normalizedEventRepository;

    private String apiKey;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        email = "job-" + UUID.randomUUID() + "@example.com";

        MvcResult magic = mockMvc.perform(post("/v1/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"Job Test\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode magicJson = objectMapper.readTree(magic.getResponse().getContentAsString());
        String link = magicJson.get("magicLink").asText();
        String token = link.substring(link.indexOf("token=") + 6);

        MvcResult verify = mockMvc.perform(post("/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String sessionToken = objectMapper.readTree(verify.getResponse().getContentAsString())
                .get("sessionToken").asText();

        MvcResult keyResult = mockMvc.perform(post("/v1/account/api-keys")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isOk())
                .andReturn();
        apiKey = objectMapper.readTree(keyResult.getResponse().getContentAsString())
                .get("apiKey").asText();
    }

    @Test
    void uploadsOfxAndNormalizesEvents() throws Exception {
        byte[] ofx = new ClassPathResource("fixtures/sample.ofx")
                .getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.ofx",
                "application/x-ofx",
                ofx
        );

        MvcResult create = mockMvc.perform(multipart("/v1/jobs")
                        .file(file)
                        .param("format", "ofx")
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andReturn();

        String jobId = objectMapper.readTree(create.getResponse().getContentAsString())
                .get("jobId").asText();

        var claimed = jobClaimer.claim(1);
        assertThat(claimed).contains(UUID.fromString(jobId));
        jobService.processJob(UUID.fromString(jobId), parserRouter);

        mockMvc.perform(get("/v1/jobs/" + jobId)
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.creditsCharged").value(1));

        MvcResult events = mockMvc.perform(get("/v1/jobs/" + jobId + "/events")
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode eventsJson = objectMapper.readTree(events.getResponse().getContentAsString());
        assertThat(eventsJson.get("events")).hasSize(2);
        assertThat(normalizedEventRepository.count()).isEqualTo(2);
    }
}

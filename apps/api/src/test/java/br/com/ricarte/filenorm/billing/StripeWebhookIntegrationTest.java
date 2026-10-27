package br.com.ricarte.filenorm.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.support.DatabaseCleaner;
import br.com.ricarte.filenorm.support.NoOpMailConfig;
import br.com.ricarte.filenorm.support.PostgresIntegrationTest;
import br.com.ricarte.filenorm.support.TestAccounts;
import br.com.ricarte.filenorm.support.TestAccounts.TestAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Eventos assinados localmente com o segredo de teste (sem chamada à Stripe). */
@AutoConfigureMockMvc
@Import(NoOpMailConfig.class)
class StripeWebhookIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

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
    void paidCheckoutGrantsPackCreditsOnceEvenWhenRepeatedOrOutOfOrder() throws Exception {
        long before = balance();

        send(event("evt_1", "checkout.session.completed", "cs_1", "paid", "starter", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("credited"));
        send(event("evt_1", "checkout.session.completed", "cs_1", "paid", "starter", "999999"))
                .andExpect(jsonPath("$.result").value("duplicate"));
        send(event("evt_2", "checkout.session.async_payment_succeeded", "cs_1", "paid", "starter", "999999"))
                .andExpect(jsonPath("$.result").value("duplicate"));

        assertThat(balance()).as("créditos do pacote resolvidos no servidor, não do metadata")
                .isEqualTo(before + BillingService.creditsForPack("starter"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE reason = 'stripe_purchase'", Long.class)).isEqualTo(1);
    }

    @Test
    void unpaidUnknownPackAndUnrelatedEventsDoNotGrantCredits() throws Exception {
        long before = balance();

        send(event("evt_3", "checkout.session.completed", "cs_2", "unpaid", "starter", "5000"))
                .andExpect(jsonPath("$.result").value("not_paid"));
        send(event("evt_4", "checkout.session.completed", "cs_3", "paid", "mega", "5000"))
                .andExpect(jsonPath("$.result").value("invalid_metadata"));
        send(event("evt_5", "customer.created", "cs_4", "paid", "starter", "5000"))
                .andExpect(jsonPath("$.result").value("ignored"));

        assertThat(balance()).isEqualTo(before);
    }

    @Test
    void refundsDoNotChangeTheBalanceAutomatically() throws Exception {
        send(event("evt_7", "checkout.session.completed", "cs_6", "paid", "starter", "5000"))
                .andExpect(jsonPath("$.result").value("credited"));
        long afterPurchase = balance();

        String refund = """
                {"id":"evt_8","object":"event","api_version":"%s","created":1720000100,"type":"charge.refunded",
                 "data":{"object":{"id":"ch_1","object":"charge","amount_refunded":5000,"refunded":true}}}
                """.formatted(Stripe.API_VERSION);
        send(refund).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("ignored"));

        // Política publicada: estorno não debita créditos automaticamente; o ajuste é manual (docs/OPERACAO.md).
        assertThat(balance()).isEqualTo(afterPurchase);
    }

    @Test
    void rejectsForgedOrUnsignedEvents() throws Exception {
        String payload = event("evt_6", "checkout.session.completed", "cs_5", "paid", "scale", "1");
        long timestamp = System.currentTimeMillis() / 1000;
        String forged = "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256("whsec_wrong", timestamp + "." + payload);

        mockMvc.perform(post("/v1/billing/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", forged).content(payload))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/billing/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE reason = 'stripe_purchase'", Long.class)).isZero();
    }

    @Test
    void checkoutRedirectsMustPointBackToTheApp() throws Exception {
        mockMvc.perform(post("/v1/billing/checkout")
                        .header("Authorization", "Bearer " + account.sessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack\":\"starter\",\"successUrl\":\"https://evil.example/ok\",\"cancelUrl\":\"https://evil.example/x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_redirect_url"));
        mockMvc.perform(post("/v1/billing/checkout")
                        .header("Authorization", "Bearer " + account.sessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack\":\"starter\",\"successUrl\":\"" + properties.auth().appBaseUrl() + "/app/billing?ok=1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("stripe_not_configured"));
    }

    private ResultActions send(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String secret = properties.billing().stripeWebhookSecret();
        String signature = "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
        return mockMvc.perform(post("/v1/billing/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private String event(String id, String type, String sessionId, String paymentStatus, String pack, String credits) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":1720000000,"type":"%s",
                 "data":{"object":{"id":"%s","object":"checkout.session","payment_status":"%s",
                   "metadata":{"accountId":"%s","pack":"%s","credits":"%s"}}}}
                """.formatted(id, Stripe.API_VERSION, type, sessionId, paymentStatus, account.accountId(), pack, credits);
    }

    private long balance() {
        return jdbc.queryForObject("SELECT credits_balance FROM accounts WHERE id = ?", Long.class, account.accountId());
    }
}

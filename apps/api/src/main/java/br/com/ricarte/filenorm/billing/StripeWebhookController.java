package br.com.ricarte.filenorm.billing;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.web.ApiException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Créditos só são concedidos aqui, a partir de evento assinado pelo Stripe — nunca pelo retorno do navegador.
 * A quantidade vem do pacote resolvido no servidor; a chave {@code stripe:checkout:<session>} torna
 * reenvios e eventos fora de ordem (completed/async_payment_succeeded) idempotentes.
 */
@RestController
@RequestMapping("/v1/billing/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final Set<String> PAID_EVENTS = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded"
    );

    private final FilenormProperties properties;
    private final CreditsService creditsService;

    public StripeWebhookController(FilenormProperties properties, CreditsService creditsService) {
        this.properties = properties;
        this.creditsService = creditsService;
    }

    @PostMapping("/webhook")
    public Map<String, String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        String secret = properties.billing().stripeWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "stripe_not_configured");
        }
        if (signature == null || signature.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_stripe_signature");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_stripe_signature");
        }
        if (!PAID_EVENTS.contains(event.getType())) {
            return Map.of("received", "true", "result", "ignored");
        }
        Session session = sessionOf(event);
        if (session == null || !"paid".equals(session.getPaymentStatus())) {
            return Map.of("received", "true", "result", "not_paid");
        }
        return Map.of("received", "true", "result", applyCredits(event, session));
    }

    private String applyCredits(Event event, Session session) {
        Map<String, String> metadata = session.getMetadata() == null ? Map.of() : session.getMetadata();
        long credits = BillingService.creditsForPack(metadata.get("pack"));
        UUID accountId;
        try {
            accountId = UUID.fromString(metadata.getOrDefault("accountId", ""));
        } catch (IllegalArgumentException ex) {
            accountId = null;
        }
        if (accountId == null || credits <= 0) {
            log.warn("stripe event {} for session {} has invalid metadata; no credits granted", event.getId(), session.getId());
            return "invalid_metadata";
        }
        try {
            boolean granted = creditsService.grant(accountId, credits, "stripe_purchase", "stripe:checkout:" + session.getId());
            return granted ? "credited" : "duplicate";
        } catch (ApiException ex) {
            log.error("stripe event {} for session {} references unknown account", event.getId(), session.getId());
            return "unknown_account";
        }
    }

    private Session sessionOf(Event event) {
        StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
        if (object == null) {
            try {
                // Versão de API do evento difere da do SDK: desserializa mesmo assim, em vez de perder o pagamento.
                object = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                log.error("stripe event {} could not be deserialized", event.getId());
                return null;
            }
        }
        return object instanceof Session session ? session : null;
    }
}

package br.com.ricarte.filenorm.billing;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.web.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing/stripe")
public class StripeWebhookController {

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
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_stripe_signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);
            if (session != null && session.getMetadata() != null) {
                applyCredits(session);
            }
        }
        return Map.of("received", "true");
    }

    private void applyCredits(Session session) {
        Map<String, String> metadata = session.getMetadata();
        String accountIdRaw = metadata.get("accountId");
        String creditsRaw = metadata.get("credits");
        if (accountIdRaw == null || creditsRaw == null) {
            return;
        }
        try {
            UUID accountId = UUID.fromString(accountIdRaw);
            long credits = Long.parseLong(creditsRaw);
            if (credits > 0) {
                creditsService.grant(accountId, credits, "stripe_purchase");
            }
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_stripe_metadata");
        }
    }
}

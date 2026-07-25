package br.com.ricarte.filenorm.billing;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.Account;
import br.com.ricarte.filenorm.domain.AccountRepository;
import br.com.ricarte.filenorm.web.ApiException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BillingService {

    private static final Map<String, Long> PACK_CREDITS = Map.of(
            "starter", 5_000L,
            "growth", 50_000L,
            "scale", 250_000L
    );

    private final AccountRepository accountRepository;
    private final FilenormProperties properties;

    public BillingService(AccountRepository accountRepository, FilenormProperties properties) {
        this.accountRepository = accountRepository;
        this.properties = properties;
    }

    public Map<String, String> createCheckoutSession(
            UUID accountId,
            String successUrl,
            String cancelUrl,
            String pack
    ) {
        ensureStripeConfigured();
        String normalizedPack = pack == null || pack.isBlank() ? "starter" : pack.trim().toLowerCase(Locale.ROOT);
        if (!PACK_CREDITS.containsKey(normalizedPack)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pack");
        }
        String priceId = priceFor(normalizedPack);
        if (priceId == null || priceId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "stripe_price_not_configured");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found"));
        try {
            Stripe.apiKey = properties.billing().stripeApiKey();
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setCustomerEmail(account.getEmail())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPrice(priceId)
                            .build())
                    .putMetadata("accountId", accountId.toString())
                    .putMetadata("pack", normalizedPack)
                    .putMetadata("credits", String.valueOf(PACK_CREDITS.get(normalizedPack)))
                    .build();
            Session session = Session.create(params);
            Map<String, String> body = new HashMap<>();
            body.put("url", session.getUrl());
            body.put("sessionId", session.getId());
            return body;
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error");
        }
    }

    public static long creditsForPack(String pack) {
        return PACK_CREDITS.getOrDefault(pack, 0L);
    }

    private String priceFor(String pack) {
        return switch (pack) {
            case "starter" -> properties.billing().stripeStarterPriceId();
            case "growth" -> properties.billing().stripeGrowthPriceId();
            case "scale" -> properties.billing().stripeScalePriceId();
            default -> null;
        };
    }

    private void ensureStripeConfigured() {
        if (properties.billing().stripeApiKey() == null || properties.billing().stripeApiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "stripe_not_configured");
        }
    }
}

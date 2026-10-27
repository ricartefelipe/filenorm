package br.com.ricarte.filenorm.billing;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.web.AccountContext;
import br.com.ricarte.filenorm.web.ApiException;
import br.com.ricarte.filenorm.web.PublicBaseUrl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private final BillingService billingService;
    private final FilenormProperties properties;

    public BillingController(BillingService billingService, FilenormProperties properties) {
        this.billingService = billingService;
        this.properties = properties;
    }

    @PostMapping("/checkout")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest request, HttpServletRequest httpRequest) {
        UUID accountId = AccountContext.requireAccountId();
        String base = PublicBaseUrl.resolve(httpRequest, properties);
        return billingService.createCheckoutSession(
                accountId,
                redirectUrl(request.successUrl(), base, "/app/billing?ok=1"),
                redirectUrl(request.cancelUrl(), base, "/app/billing?cancel=1"),
                request.pack()
        );
    }

    /** O retorno do Stripe só pode levar de volta ao próprio painel (evita open redirect). */
    private String redirectUrl(String requested, String base, String defaultPath) {
        if (requested == null || requested.isBlank()) {
            return base + defaultPath;
        }
        String appBase = properties.auth().appBaseUrl().replaceAll("/$", "");
        if (requested.startsWith(base + "/") || requested.startsWith(appBase + "/")) {
            return requested;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_redirect_url");
    }

    public record CheckoutRequest(String successUrl, String cancelUrl, String pack) {
    }
}

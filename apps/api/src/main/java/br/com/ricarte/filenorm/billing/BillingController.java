package br.com.ricarte.filenorm.billing;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.web.AccountContext;
import br.com.ricarte.filenorm.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/checkout")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest request) {
        UUID accountId = AccountContext.requireAccountId();
        return billingService.createCheckoutSession(
                accountId,
                request.successUrl(),
                request.cancelUrl(),
                request.pack()
        );
    }

    public record CheckoutRequest(
            @NotBlank String successUrl,
            @NotBlank String cancelUrl,
            String pack
    ) {
    }
}

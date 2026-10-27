package br.com.ricarte.filenorm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "filenorm")
public record FilenormProperties(
        Worker worker,
        Auth auth,
        Credits credits,
        Jobs jobs,
        Storage storage,
        Billing billing,
        Cors cors,
        Totalrecall totalrecall
) {
    public record Worker(
            boolean enabled,
            long pollIntervalMs,
            int batchSize,
            @DefaultValue("300") long leaseSeconds,
            @DefaultValue("3") int maxAttempts
    ) {
    }

    public record Auth(
            String appBaseUrl,
            String apiBaseUrl,
            String fromEmail,
            int magicLinkTtlMinutes,
            int sessionTtlDays,
            boolean exposeMagicLink,
            boolean trustForwardedHost
    ) {
    }

    public record Credits(
            long signupGrant,
            int ofxCost,
            int csvCost,
            int cnabCost,
            int pdfCostPerPage,
            int pdfMinCost
    ) {
    }

    public record Jobs(
            long maxUploadBytes,
            @DefaultValue("50000") int maxRecords,
            @DefaultValue("100") int maxPdfPages,
            @DefaultValue("0") int retentionDays
    ) {
    }

    public record Storage(String path) {
    }

    public record Billing(
            String stripeApiKey,
            String stripeWebhookSecret,
            String stripeStarterPriceId,
            String stripeGrowthPriceId,
            String stripeScalePriceId
    ) {
    }

    public record Cors(String allowedOrigins) {
    }

    public record Totalrecall(String provisionToken) {
    }
}

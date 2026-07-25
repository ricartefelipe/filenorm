package br.com.ricarte.filenorm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "filenorm")
public record FilenormProperties(
        Worker worker,
        Auth auth,
        Credits credits,
        Jobs jobs,
        Storage storage,
        Billing billing,
        Cors cors
) {
    public record Worker(boolean enabled, long pollIntervalMs, int batchSize) {
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

    public record Jobs(long maxUploadBytes) {
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
}

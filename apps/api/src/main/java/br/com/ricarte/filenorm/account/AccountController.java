package br.com.ricarte.filenorm.account;

import br.com.ricarte.filenorm.auth.TokenHasher;
import br.com.ricarte.filenorm.domain.ApiKey;
import br.com.ricarte.filenorm.domain.ApiKeyRepository;
import br.com.ricarte.filenorm.domain.Job;
import br.com.ricarte.filenorm.domain.JobRepository;
import br.com.ricarte.filenorm.web.AccountContext;
import br.com.ricarte.filenorm.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account")
public class AccountController {

    private final ApiKeyRepository apiKeyRepository;
    private final JobRepository jobRepository;
    private final UsageService usageService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountController(
            ApiKeyRepository apiKeyRepository,
            JobRepository jobRepository,
            UsageService usageService
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.jobRepository = jobRepository;
        this.usageService = usageService;
    }

    @GetMapping("/usage")
    public Map<String, Object> usage() {
        return usageService.usage(AccountContext.requireAccountId());
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> listJobs() {
        UUID accountId = AccountContext.requireAccountId();
        return jobRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .limit(50)
                .map(this::jobSummary)
                .toList();
    }

    @PostMapping("/api-keys")
    public Map<String, Object> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        UUID accountId = AccountContext.requireAccountId();
        String rawKey = "fn_live_" + randomHex(24);
        String prefix = rawKey.substring(0, 16);
        Instant now = Instant.now();
        ApiKey apiKey = new ApiKey(
                UUID.randomUUID(),
                accountId,
                request.name(),
                prefix,
                TokenHasher.sha256(rawKey),
                now
        );
        apiKeyRepository.save(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", apiKey.getId().toString());
        body.put("name", apiKey.getName());
        body.put("keyPrefix", apiKey.getKeyPrefix());
        body.put("apiKey", rawKey);
        body.put("createdAt", apiKey.getCreatedAt().toString());
        return body;
    }

    @GetMapping("/api-keys")
    public List<Map<String, Object>> listApiKeys() {
        UUID accountId = AccountContext.requireAccountId();
        return apiKeyRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(key -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", key.getId().toString());
                    item.put("name", key.getName());
                    item.put("keyPrefix", key.getKeyPrefix());
                    item.put("revoked", key.getRevokedAt() != null);
                    item.put("createdAt", key.getCreatedAt().toString());
                    return item;
                })
                .toList();
    }

    @DeleteMapping("/api-keys/{id}")
    public Map<String, Boolean> revokeApiKey(@PathVariable UUID id) {
        UUID accountId = AccountContext.requireAccountId();
        ApiKey apiKey = apiKeyRepository.findById(id)
                .filter(key -> key.getAccountId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_key_not_found"));
        if (apiKey.active()) {
            apiKey.revoke(Instant.now());
            apiKeyRepository.save(apiKey);
        }
        return Map.of("ok", true);
    }

    private Map<String, Object> jobSummary(Job job) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", job.getId().toString());
        item.put("status", job.getStatus());
        item.put("format", job.getFormat());
        item.put("originalFilename", job.getOriginalFilename());
        item.put("creditsCharged", job.getCreditsCharged());
        item.put("errorCode", job.getErrorCode());
        item.put("createdAt", job.getCreatedAt().toString());
        item.put("finishedAt", job.getFinishedAt() == null ? null : job.getFinishedAt().toString());
        return item;
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    public record CreateApiKeyRequest(@NotBlank String name) {
    }
}

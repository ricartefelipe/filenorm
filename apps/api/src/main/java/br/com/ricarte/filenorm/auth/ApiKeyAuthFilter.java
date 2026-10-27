package br.com.ricarte.filenorm.auth;

import br.com.ricarte.filenorm.domain.AccountRepository;
import br.com.ricarte.filenorm.domain.ApiKey;
import br.com.ricarte.filenorm.domain.ApiKeyRepository;
import br.com.ricarte.filenorm.web.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final AccountRepository accountRepository;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository, AccountRepository accountRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isCorsPreflight(request) || !path.startsWith("/v1/jobs")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Optional<UUID> accountId = resolveApiKey(request);
            if (accountId.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"unauthorized\"}");
                return;
            }
            AccountContext.set(accountId.get());
            filterChain.doFilter(request, response);
        } finally {
            AccountContext.clear();
        }
    }

    private Optional<UUID> resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring("Bearer ".length()).trim();
        if (!token.startsWith("fn_live_")) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(TokenHasher.sha256(token))
                .filter(ApiKey::active)
                .map(ApiKey::getAccountId)
                // Conta desativada ou expirada (ex.: provisionamento TotalRecall) perde também o acesso por API key.
                .filter(accountId -> accountRepository.findById(accountId)
                        .map(account -> account.isActive(Instant.now()))
                        .orElse(false));
    }

    /** Preflight não carrega credenciais; quem decide é a configuração de CORS. */
    private static boolean isCorsPreflight(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null;
    }
}

package br.com.ricarte.filenorm.auth;

import br.com.ricarte.filenorm.domain.ApiKey;
import br.com.ricarte.filenorm.domain.ApiKeyRepository;
import br.com.ricarte.filenorm.web.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/v1/jobs")) {
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
                .map(ApiKey::getAccountId);
    }
}

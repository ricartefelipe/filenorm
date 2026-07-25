package br.com.ricarte.filenorm.auth;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public SessionAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!requiresSession(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Optional<UUID> accountId = resolveSession(request);
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

    private Optional<UUID> resolveSession(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            if (!token.startsWith("fn_live_")) {
                return authService.resolveAccountId(token);
            }
        }
        return Optional.empty();
    }

    private boolean requiresSession(String path) {
        if (path.startsWith("/v1/account/")) {
            return true;
        }
        if (path.startsWith("/v1/billing/checkout")) {
            return true;
        }
        if (path.equals("/v1/auth/me") || path.equals("/v1/auth/logout")) {
            return true;
        }
        return false;
    }
}

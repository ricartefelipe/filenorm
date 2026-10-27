package br.com.ricarte.filenorm.auth;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.web.AccountContext;
import br.com.ricarte.filenorm.web.PublicBaseUrl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final FilenormProperties properties;

    public AuthController(AuthService authService, FilenormProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/magic-link")
    public Map<String, Object> magicLink(
            @Valid @RequestBody MagicLinkRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.requestMagicLink(
                request.email(),
                request.name(),
                PublicBaseUrl.resolve(httpRequest, properties),
                revealMagicLink(request)
        );
    }

    /**
     * Modo demo: devolver o link na resposta equivale a login sem e-mail, então só acontece com o
     * código de acesso da demo. Origem (IP/loopback) não serve de prova: os cabeçalhos de proxy são
     * aceitos (forward-headers-strategy) e poderiam ser forjados. Sem código, o link vai só por e-mail.
     */
    private boolean revealMagicLink(MagicLinkRequest request) {
        String code = properties.auth().demoAccessCode();
        return properties.auth().exposeMagicLink()
                && code != null && !code.isBlank()
                && request.accessCode() != null
                && MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), request.accessCode().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@Valid @RequestBody VerifyRequest request) {
        return authService.verifyMagicLink(request.token());
    }

    @PostMapping("/password")
    public Map<String, Object> password(@Valid @RequestBody PasswordRequest request) {
        return authService.loginWithPassword(request.email(), request.password());
    }

    @GetMapping("/verify")
    public Map<String, Object> verifyGet(@RequestParam String token) {
        return authService.verifyMagicLink(token);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.me(AccountContext.requireAccountId());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        String bearer = extractBearer(request);
        if (bearer != null) {
            authService.logout(bearer);
        }
        return Map.of("ok", true);
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length()).trim();
        }
        return null;
    }

    public record MagicLinkRequest(@NotBlank @Email String email, String name, String accessCode) {
    }

    public record VerifyRequest(@NotBlank String token) {
    }

    public record PasswordRequest(@NotBlank @Email String email, @NotBlank String password) {
    }
}

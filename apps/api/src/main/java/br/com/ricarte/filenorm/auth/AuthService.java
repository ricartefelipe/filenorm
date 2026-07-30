package br.com.ricarte.filenorm.auth;

import br.com.ricarte.filenorm.billing.CreditsService;
import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.Account;
import br.com.ricarte.filenorm.domain.AccountRepository;
import br.com.ricarte.filenorm.domain.LoginToken;
import br.com.ricarte.filenorm.domain.LoginTokenRepository;
import br.com.ricarte.filenorm.domain.Session;
import br.com.ricarte.filenorm.domain.SessionRepository;
import br.com.ricarte.filenorm.web.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final LoginTokenRepository loginTokenRepository;
    private final SessionRepository sessionRepository;
    private final CreditsService creditsService;
    private final FilenormProperties properties;
    private final JavaMailSender mailSender;
    private final TotalRecallClient totalRecallClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AccountRepository accountRepository,
            LoginTokenRepository loginTokenRepository,
            SessionRepository sessionRepository,
            CreditsService creditsService,
            FilenormProperties properties,
            JavaMailSender mailSender,
            TotalRecallClient totalRecallClient
    ) {
        this.accountRepository = accountRepository;
        this.loginTokenRepository = loginTokenRepository;
        this.sessionRepository = sessionRepository;
        this.creditsService = creditsService;
        this.properties = properties;
        this.mailSender = mailSender;
        this.totalRecallClient = totalRecallClient;
    }

    @Transactional
    public Account ensureAccount(String email, String name) {
        return accountRepository.findByEmail(email).orElseGet(() -> {
            Instant now = Instant.now();
            Account account = new Account(UUID.randomUUID(), email, name, 0, now);
            accountRepository.save(account);
            creditsService.grantSignup(account.getId());
            return accountRepository.findById(account.getId()).orElseThrow();
        });
    }

    @Transactional
    public Map<String, Object> requestMagicLink(String email, String name) {
        return requestMagicLink(email, name, null);
    }

    @Transactional
    public Map<String, Object> requestMagicLink(String email, String name, String appBaseUrlOverride) {
        Account account = ensureAccount(email, name == null || name.isBlank() ? email : name);
        String rawToken = randomToken(32);
        Instant now = Instant.now();
        LoginToken loginToken = new LoginToken(
                UUID.randomUUID(),
                account.getId(),
                TokenHasher.sha256(rawToken),
                now.plus(properties.auth().magicLinkTtlMinutes(), ChronoUnit.MINUTES),
                now
        );
        loginTokenRepository.save(loginToken);

        String base = appBaseUrlOverride == null || appBaseUrlOverride.isBlank()
                ? properties.auth().appBaseUrl()
                : appBaseUrlOverride;
        String link = base.replaceAll("/$", "") + "/auth/callback?token=" + rawToken;
        sendMail(account.getEmail(), link);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sent", true);
        body.put("email", account.getEmail());
        if (properties.auth().exposeMagicLink()) {
            body.put("magicLink", link);
        }
        return body;
    }

    @Transactional
    public Map<String, Object> verifyMagicLink(String rawToken) {
        Optional<LoginToken> local = loginTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken));
        if (local.isPresent()) {
            LoginToken loginToken = local.get();
            Instant now = Instant.now();
            if (loginToken.getExpiresAt().isBefore(now)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token");
            }
            loginTokenRepository.delete(loginToken);

            Account account = accountRepository.findById(loginToken.getAccountId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token"));
            return createSessionForAccount(account);
        }

        Map<String, Object> tr = totalRecallClient.validateToken(rawToken);
        if (!Boolean.TRUE.equals(tr.get("valid"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) tr.get("profile");
        String email = profile == null ? null : String.valueOf(profile.get("email"));
        String name = profile == null ? email : String.valueOf(profile.getOrDefault("name", email));
        if (email == null || email.isBlank() || "null".equals(email)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token");
        }
        return createSessionForAccount(ensureAccount(email, name));
    }

    @Transactional
    public Map<String, Object> loginWithPassword(String email, String password) {
        Map<String, Object> tr = totalRecallClient.login(email, password);
        if (!Boolean.TRUE.equals(tr.get("valid"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) tr.get("profile");
        String name = profile == null ? email : String.valueOf(profile.getOrDefault("name", email));
        return createSessionForAccount(ensureAccount(email, name));
    }

    @Transactional
    public Map<String, Object> createSessionForAccount(Account account) {
        Instant now = Instant.now();
        String sessionRaw = randomToken(32);
        Session session = new Session(
                UUID.randomUUID(),
                account.getId(),
                TokenHasher.sha256(sessionRaw),
                now.plus(properties.auth().sessionTtlDays(), ChronoUnit.DAYS),
                now
        );
        sessionRepository.save(session);
        return sessionResponse(account, sessionRaw);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveAccountId(String sessionRaw) {
        if (sessionRaw == null || sessionRaw.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findByTokenHash(TokenHasher.sha256(sessionRaw))
                .filter(session -> session.active(Instant.now()))
                .map(Session::getAccountId);
    }

    @Transactional
    public void logout(String sessionRaw) {
        sessionRepository.findByTokenHash(TokenHasher.sha256(sessionRaw))
                .ifPresent(sessionRepository::delete);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", account.getId().toString());
        body.put("email", account.getEmail());
        body.put("name", account.getName());
        body.put("creditsBalance", account.getCreditsBalance());
        return body;
    }

    private Map<String, Object> sessionResponse(Account account, String sessionRaw) {
        Map<String, Object> body = me(account.getId());
        body.put("sessionToken", sessionRaw);
        return body;
    }

    private void sendMail(String to, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.auth().fromEmail());
        message.setTo(to);
        message.setSubject("Seu acesso ao FileNorm");
        message.setText("Use este link para entrar (expira em breve):\n\n" + link + "\n");
        mailSender.send(message);
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}

package br.com.ricarte.filenorm.billing;

import br.com.ricarte.filenorm.config.FilenormProperties;
import br.com.ricarte.filenorm.domain.Account;
import br.com.ricarte.filenorm.domain.AccountRepository;
import br.com.ricarte.filenorm.domain.CreditLedgerEntry;
import br.com.ricarte.filenorm.domain.CreditLedgerRepository;
import br.com.ricarte.filenorm.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditsService {

    private final AccountRepository accountRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final FilenormProperties properties;

    public CreditsService(
            AccountRepository accountRepository,
            CreditLedgerRepository creditLedgerRepository,
            FilenormProperties properties
    ) {
        this.accountRepository = accountRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.properties = properties;
    }

    @Transactional
    public void grantSignup(UUID accountId) {
        applyDelta(accountId, properties.credits().signupGrant(), "signup_grant", null);
    }

    @Transactional
    public void grant(UUID accountId, long amount, String reason) {
        applyDelta(accountId, amount, reason, null);
    }

    @Transactional(readOnly = true)
    public void requireAvailable(UUID accountId, long amount) {
        Account account = load(accountId);
        if (account.getCreditsBalance() < amount) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "insufficient_credits");
        }
    }

    @Transactional
    public void charge(UUID accountId, long amount, String reason, UUID jobId) {
        Account account = load(accountId);
        if (account.getCreditsBalance() < amount) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "insufficient_credits");
        }
        applyDelta(accountId, -amount, reason, jobId);
    }

    private void applyDelta(UUID accountId, long delta, String reason, UUID jobId) {
        Account account = load(accountId);
        account.setCreditsBalance(account.getCreditsBalance() + delta);
        accountRepository.save(account);
        creditLedgerRepository.save(new CreditLedgerEntry(
                UUID.randomUUID(),
                accountId,
                delta,
                reason,
                jobId,
                Instant.now()
        ));
    }

    private Account load(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found"));
    }
}

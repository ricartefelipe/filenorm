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

/**
 * Saldo e ledger de créditos. Toda movimentação usa UPDATE condicional no banco (sem
 * leitura-e-escrita em memória) e grava uma linha de ledger com {@code operation_key} única,
 * de modo que repetir a mesma operação não move o saldo duas vezes.
 */
@Service
public class CreditsService {

    public enum ChargeResult { CHARGED, ALREADY_APPLIED, INSUFFICIENT }

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
        grant(accountId, properties.credits().signupGrant(), "signup_grant", "signup:" + accountId);
    }

    /** @return false quando a operação já tinha sido aplicada. */
    @Transactional
    public boolean grant(UUID accountId, long amount, String reason, String operationKey) {
        if (operationKey != null && creditLedgerRepository.existsByOperationKey(operationKey)) {
            return false;
        }
        if (accountRepository.credit(accountId, amount) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "account_not_found");
        }
        creditLedgerRepository.save(new CreditLedgerEntry(
                UUID.randomUUID(), accountId, amount, reason, null, operationKey, Instant.now()));
        return true;
    }

    @Transactional(readOnly = true)
    public void requireAvailable(UUID accountId, long amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found"));
        if (account.getCreditsBalance() < amount) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "insufficient_credits");
        }
    }

    /**
     * Debita sem lançar exceção de negócio, para não marcar a transação do chamador como rollback-only.
     */
    @Transactional
    public ChargeResult charge(UUID accountId, long amount, String reason, UUID jobId, String operationKey) {
        if (creditLedgerRepository.existsByOperationKey(operationKey)) {
            return ChargeResult.ALREADY_APPLIED;
        }
        if (accountRepository.debitIfAvailable(accountId, amount) == 0) {
            return ChargeResult.INSUFFICIENT;
        }
        creditLedgerRepository.save(new CreditLedgerEntry(
                UUID.randomUUID(), accountId, -amount, reason, jobId, operationKey, Instant.now()));
        return ChargeResult.CHARGED;
    }
}

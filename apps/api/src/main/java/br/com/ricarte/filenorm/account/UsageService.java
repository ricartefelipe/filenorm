package br.com.ricarte.filenorm.account;

import br.com.ricarte.filenorm.domain.Account;
import br.com.ricarte.filenorm.domain.AccountRepository;
import br.com.ricarte.filenorm.domain.CreditLedgerEntry;
import br.com.ricarte.filenorm.domain.CreditLedgerRepository;
import br.com.ricarte.filenorm.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {

    private final AccountRepository accountRepository;
    private final CreditLedgerRepository creditLedgerRepository;

    public UsageService(AccountRepository accountRepository, CreditLedgerRepository creditLedgerRepository) {
        this.accountRepository = accountRepository;
        this.creditLedgerRepository = creditLedgerRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> usage(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found"));
        List<CreditLedgerEntry> recent = creditLedgerRepository
                .findTop20ByAccountIdOrderByCreatedAtDesc(accountId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("creditsBalance", account.getCreditsBalance());
        body.put("recentLedger", recent.stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("delta", entry.getDelta());
            item.put("reason", entry.getReason());
            item.put("jobId", entry.getJobId() == null ? null : entry.getJobId().toString());
            item.put("createdAt", entry.getCreatedAt().toString());
            return item;
        }).toList());
        return body;
    }
}

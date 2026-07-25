package br.com.ricarte.filenorm.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, UUID> {

    List<CreditLedgerEntry> findTop20ByAccountIdOrderByCreatedAtDesc(UUID accountId);
}

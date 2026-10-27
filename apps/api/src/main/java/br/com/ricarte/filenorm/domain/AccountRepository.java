package br.com.ricarte.filenorm.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);

    /** Débito atômico: só altera o saldo se houver créditos suficientes (0 linhas = saldo insuficiente). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts SET credits_balance = credits_balance - :amount
            WHERE id = :id AND credits_balance >= :amount
            """, nativeQuery = true)
    int debitIfAvailable(@Param("id") UUID id, @Param("amount") long amount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE accounts SET credits_balance = credits_balance + :amount WHERE id = :id", nativeQuery = true)
    int credit(@Param("id") UUID id, @Param("amount") long amount);
}

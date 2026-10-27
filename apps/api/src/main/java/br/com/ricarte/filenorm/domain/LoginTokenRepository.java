package br.com.ricarte.filenorm.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginTokenRepository extends JpaRepository<LoginToken, UUID> {

    Optional<LoginToken> findByTokenHash(String tokenHash);

    /** Consumo de uso único: com acessos simultâneos ao mesmo link, só um apaga a linha (1); os demais recebem 0. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM LoginToken t WHERE t.tokenHash = :tokenHash AND t.expiresAt > :now")
    int consume(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}

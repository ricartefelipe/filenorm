package br.com.ricarte.filenorm.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByAccountIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID accountId);

    List<ApiKey> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}

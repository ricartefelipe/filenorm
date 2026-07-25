package br.com.ricarte.filenorm.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByTokenHash(String tokenHash);
}

package br.com.ricarte.filenorm.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedEventRepository extends JpaRepository<NormalizedEvent, UUID> {

    List<NormalizedEvent> findByJobIdOrderByOrdinalAsc(UUID jobId);
}

package sidekick.ingestion.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {

    List<ChunkEntity> findByFundId(String fundId);

    void deleteByFundId(String fundId);
}

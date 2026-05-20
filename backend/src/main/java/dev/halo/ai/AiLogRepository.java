package dev.halo.ai;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiLogRepository extends JpaRepository<AiLog, UUID> {
}

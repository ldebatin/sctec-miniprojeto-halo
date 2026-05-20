package dev.halo.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessage, UUID> {

    Optional<WhatsappMessage> findByEvolutionMsgId(String evolutionMsgId);

    boolean existsByEvolutionMsgId(String evolutionMsgId);
}

package dev.halo.whatsapp.conversation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationStateRepository extends JpaRepository<ConversationState, UUID> {

    Optional<ConversationState> findByPhone(String phone);

    void deleteByPhone(String phone);
}

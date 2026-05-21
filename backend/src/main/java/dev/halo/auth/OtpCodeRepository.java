package dev.halo.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositório JPA para {@link OtpCode}.
 */
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    /**
     * Retorna o OTP mais recente para o telefone que ainda não foi usado e não expirou.
     * Usado na verificação do código.
     */
    @Query("""
            SELECT o FROM OtpCode o
            WHERE o.phone = :phone
              AND o.usedAt IS NULL
              AND o.expiresAt > :now
            ORDER BY o.createdAt DESC
            LIMIT 1
            """)
    Optional<OtpCode> findLatestValid(@Param("phone") String phone, @Param("now") Instant now);

    /**
     * Retorna o OTP mais recente para o telefone (independente de status),
     * usado para checar o cooldown de 60s.
     */
    @Query("""
            SELECT o FROM OtpCode o
            WHERE o.phone = :phone
            ORDER BY o.createdAt DESC
            LIMIT 1
            """)
    Optional<OtpCode> findLatest(@Param("phone") String phone);
}

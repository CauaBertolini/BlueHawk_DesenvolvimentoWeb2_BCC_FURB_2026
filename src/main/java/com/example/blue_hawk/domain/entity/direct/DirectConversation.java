package com.example.blue_hawk.domain.entity.direct;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Conversa direta entre dois usuários do site (fora das partidas e das negociações). Uma por par: o par é
 * guardado em ordem ({@code userAId < userBId}, comparando o texto do id, como o CHAR(36) do banco).
 */
public class DirectConversation {

    private final UUID id;
    private final UUID userAId;
    private final UUID userBId;
    /** null = ainda sem mensagens */
    private Instant lastMessageAt;
    private final Instant createdAt;
    private final UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;

    public DirectConversation(UUID id, UUID userAId, UUID userBId, Instant lastMessageAt, Instant createdAt,
                              UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.userAId = userAId;
        this.userBId = userBId;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /** Nova conversa entre quem começou ({@code starter}) e {@code other}; o par é ordenado aqui */
    public static DirectConversation start(UUID starter, UUID other) {
        if (starter.equals(other)) {
            throw new IllegalArgumentException("A conversation needs two different users");
        }
        UUID[] pair = orderedPair(starter, other);
        return new DirectConversation(UUID.randomUUID(), pair[0], pair[1], null, now(), starter, null, null);
    }

    /** O par na ordem gravada no banco (menor id primeiro, comparando o texto) */
    public static UUID[] orderedPair(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? new UUID[]{a, b} : new UUID[]{b, a};
    }

    public boolean isParticipant(UUID userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    /** A outra pessoa da conversa, vista por {@code userId} (que precisa ser participante) */
    public UUID otherThan(UUID userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    /** Nova mensagem: atualiza a última atividade */
    public void messageSent(UUID senderId, Instant at) {
        this.lastMessageAt = at;
        this.updatedAt = at;
        this.updatedBy = senderId;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserAId() {
        return userAId;
    }

    public UUID getUserBId() {
        return userBId;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}

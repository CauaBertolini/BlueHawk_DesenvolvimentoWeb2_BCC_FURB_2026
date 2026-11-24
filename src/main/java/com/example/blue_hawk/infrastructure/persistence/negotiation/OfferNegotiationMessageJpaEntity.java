package com.example.blue_hawk.infrastructure.persistence.negotiation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "offer_negotiation_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfferNegotiationMessageJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "negotiation_id", nullable = false, length = 36)
    private UUID negotiationId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "sender_id", nullable = false, length = 36)
    private UUID senderId;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    /** null = sem proposta */
    @Column(name = "proposed_price", precision = 10, scale = 2)
    private BigDecimal proposedPrice;

    /** UTC */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "created_by", length = 36)
    private UUID createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "updated_by", length = 36)
    private UUID updatedBy;
}

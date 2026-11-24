package com.example.blue_hawk.infrastructure.persistence.negotiation;

import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "offer_negotiation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfferNegotiationJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "offer_id", nullable = false, length = 36)
    private UUID offerId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "buyer_id", nullable = false, length = 36)
    private UUID buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OfferNegotiation.Status status;

    @Column(name = "agreed_price", precision = 10, scale = 2)
    private BigDecimal agreedPrice;

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

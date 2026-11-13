package dev.bootcamp.yankiservice.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Asociación de una tarjeta con un monedero tal como se guarda en la colección
 * {@code debit_card_links}.
 */
@Getter
@AllArgsConstructor
@Document(collection = "debit_card_links")
public class DebitCardLinkDocument {
    @Id private final String id;
    private final String customerId;
    private final String debitCardId;
    @Indexed private final String walletId;
    private final String phone;
    private final LocalDate requestedAt;

    /**
     * PENDING_CONFIRMATION, TRANSFERRING, ACTIVE o REJECTED.
     */
    private final String status;

    @Field(targetType = FieldType.DECIMAL128)
    private final BigDecimal transferredBalance;

    private final String rejectionReason;
}

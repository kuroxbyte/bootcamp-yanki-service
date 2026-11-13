package dev.bootcamp.yankiservice.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Copia de una tarjeta de débito tal como se guarda en la colección {@code debit_cards}; su id es
 * el de account-service.
 */
@Getter
@AllArgsConstructor
@Document(collection = "debit_cards")
public class DebitCardDocument {
    @Id private final String id;
    private final String customerId;
    private final String primaryAccountId;
}

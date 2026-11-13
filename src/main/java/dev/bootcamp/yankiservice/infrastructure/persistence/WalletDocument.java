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
 * Monedero tal como se guarda en la colección {@code wallets}.
 *
 * <p>Los índices únicos impiden dos monederos con el mismo celular y una tarjeta en dos monederos
 * aunque lleguen dos pedidos a la vez. El de la tarjeta es disperso: los monederos sin tarjeta no
 * tienen el campo.
 */
@Getter
@AllArgsConstructor
@Document(collection = "wallets")
public class WalletDocument {
    @Id private final String id;
    private final String documentType;
    private final String documentNumber;
    @Indexed(unique = true) private final String phone;
    private final String imei;
    private final String email;

    /**
     * Saldo propio; Decimal128 para poder sumarlo y compararlo de forma atómica.
     */
    @Field(targetType = FieldType.DECIMAL128)
    private final BigDecimal balance;

    @Indexed(unique = true, sparse = true) private final String debitCardId;

    /**
     * ACTIVE o LINKING.
     */
    private final String status;

    private final LocalDate createdAt;
}

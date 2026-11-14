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
 * <p>Los índices únicos impiden dos monederos activos con el mismo celular y una tarjeta en dos
 * monederos aunque lleguen dos pedidos a la vez. Ambos son dispersos: los monederos sin tarjeta no
 * tienen {@code debitCardId}, y uno dado de baja guarda su celular en {@code closedPhone} y no
 * tiene {@code phone}, así el celular queda libre para un monedero nuevo.
 */
@Getter
@AllArgsConstructor
@Document(collection = "wallets")
public class WalletDocument {
    @Id private final String id;
    private final String documentType;
    private final String documentNumber;
    /**
     * Celular; null si el monedero fue dado de baja.
     */
    @Indexed(unique = true, sparse = true) private final String phone;
    private final String imei;
    private final String email;

    /**
     * Saldo propio; Decimal128 para poder sumarlo y compararlo de forma atómica.
     */
    @Field(targetType = FieldType.DECIMAL128)
    private final BigDecimal balance;

    @Indexed(unique = true, sparse = true) private final String debitCardId;

    /**
     * ACTIVE, LINKING o CLOSED.
     */
    private final String status;

    private final LocalDate createdAt;

    /**
     * Celular que tenía el monedero al darse de baja.
     */
    private final String closedPhone;
}

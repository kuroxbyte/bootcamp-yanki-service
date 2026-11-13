package dev.bootcamp.yankiservice.domain.model.link;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Asociación de una tarjeta de débito del banco con un monedero. Tiene dos pasos: el cliente dueño
 * de la tarjeta la autoriza y el dueño del monedero la confirma; así nadie asocia su tarjeta a un
 * monedero ajeno para quedarse con su saldo.
 */
@Getter
public class DebitCardLink {
    @Setter private String id;
    private final String customerId;
    private final String debitCardId;
    private final String walletId;
    private final String phone;
    private final LocalDate requestedAt;
    @Setter private LinkStatus status;

    /**
     * Saldo del monedero que se transfiere a la cuenta principal al confirmar.
     */
    @Setter private BigDecimal transferredBalance;

    @Setter private String rejectionReason;

    /**
     * Crea una asociación pendiente de confirmación.
     */
    public DebitCardLink(
            String customerId,
            String debitCardId,
            String walletId,
            String phone,
            LocalDate requestedAt) {
        this.customerId = customerId;
        this.debitCardId = debitCardId;
        this.walletId = walletId;
        this.phone = phone;
        this.requestedAt = requestedAt;
        this.status = LinkStatus.PENDING_CONFIRMATION;
    }
}

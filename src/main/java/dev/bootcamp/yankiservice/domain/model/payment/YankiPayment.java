package dev.bootcamp.yankiservice.domain.model.payment;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pago entre dos monederos Yanki, identificados por su celular.
 *
 * <p>Cada lado usa el saldo de su monedero o, si tiene tarjeta de débito, la cuenta principal de la
 * tarjeta ({@code sourceAccountId} / {@code targetAccountId}; null si usa el saldo del monedero).
 */
@Getter
public class YankiPayment {
    @Setter private String id;
    private final String sourceWalletId;
    private final String sourcePhone;
    private final String sourceAccountId;
    private final String targetWalletId;
    private final String targetPhone;
    private final String targetAccountId;
    private final BigDecimal amount;
    private final LocalDate date;
    @Setter private PaymentStatus status;
    @Setter private PaymentStep step;
    @Setter private String rejectionReason;

    /**
     * Crea un pago pendiente validando el monto y que no sea a uno mismo.
     *
     * @throws BusinessRuleException si el monto no es mayor a 0 o el destino es el mismo monedero
     */
    public YankiPayment(
            String sourceWalletId,
            String sourcePhone,
            String sourceAccountId,
            String targetWalletId,
            String targetPhone,
            String targetAccountId,
            BigDecimal amount,
            LocalDate date) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("El monto debe ser mayor a 0");
        }
        if (sourceWalletId.equals(targetWalletId)) {
            throw new BusinessRuleException("No se puede enviar un pago al propio monedero");
        }
        this.sourceWalletId = sourceWalletId;
        this.sourcePhone = sourcePhone;
        this.sourceAccountId = sourceAccountId;
        this.targetWalletId = targetWalletId;
        this.targetPhone = targetPhone;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.date = date;
        this.status = PaymentStatus.PENDING;
        this.step = sourceAccountId != null
                ? PaymentStep.DEBITING_SOURCE
                : PaymentStep.CREDITING_TARGET;
    }

    /**
     * Indica si el origen paga desde la cuenta de su tarjeta de débito.
     */
    public boolean fromAccount() {
        return sourceAccountId != null;
    }

    /**
     * Indica si el destino recibe en la cuenta de su tarjeta de débito.
     */
    public boolean toAccount() {
        return targetAccountId != null;
    }

    /**
     * Marca el pago como completado.
     */
    public void complete() {
        status = PaymentStatus.COMPLETED;
        step = PaymentStep.DONE;
    }

    /**
     * Marca el pago como rechazado con su motivo.
     */
    public void reject(String reason) {
        status = PaymentStatus.REJECTED;
        step = PaymentStep.DONE;
        rejectionReason = reason;
    }
}

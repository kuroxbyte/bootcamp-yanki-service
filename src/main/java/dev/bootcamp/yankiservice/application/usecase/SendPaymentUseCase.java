package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import io.reactivex.rxjava3.core.Single;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Caso de uso para enviar un pago a otro monedero con solo su número de celular.
 *
 * <p>Cada lado usa el saldo de su monedero o, si tiene tarjeta de débito, la cuenta principal de la
 * tarjeta:
 *
 * <ul>
 *   <li>Monedero a monedero: se descuenta y acredita en el momento ({@code COMPLETED}); el saldo
 *       debe alcanzar.
 *   <li>Monedero a cuenta: se descuenta del monedero y se pide el abono a la cuenta del destino
 *       ({@code PENDING}); si el abono se rechaza, el saldo vuelve al monedero.
 *   <li>Cuenta a monedero o a cuenta: se pide el cargo a la cuenta del origen ({@code PENDING}),
 *       con las reglas y la comisión de un retiro; si se acepta, se acredita al destino. Si el
 *       abono a una cuenta destino se rechaza, el cargo se devuelve.
 * </ul>
 *
 * <p>Ambos monederos deben estar activos (no asociando una tarjeta) y el monto debe ser mayor a 0.
 * Es reactivo: no hace nada hasta que se suscribe al {@link Single}.
 */
public interface SendPaymentUseCase {

    /**
     * Envía el pago.
     *
     * @param walletId    monedero que paga
     * @param targetPhone celular del monedero que recibe
     * @param amount      monto
     * @param date        fecha del pago
     * @return el pago, {@code COMPLETED} o {@code PENDING}; error {@link NotFoundException} si el
     *     celular no tiene monedero; {@link BusinessRuleException} si se incumple una regla; {@link
     *     OperationUnavailableException} si no se pudo pedir la operación a la cuenta (lo
     *     descontado se devolvió)
     */
    Single<YankiPayment> execute(
            String walletId,
            String targetPhone,
            BigDecimal amount,
            LocalDate date);
}

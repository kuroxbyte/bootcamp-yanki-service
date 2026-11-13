package dev.bootcamp.yankiservice.application.usecase;

import io.reactivex.rxjava3.core.Completable;

/**
 * Caso de uso para aplicar la respuesta de transaction-service a una operación sobre una cuenta
 * bancaria y avanzar el pago o la asociación a la que pertenece.
 *
 * <ul>
 *   <li>Cargo del origen aceptado: se acredita el destino (en su monedero, o se pide el abono a su
 *       cuenta). Rechazado: el pago queda rechazado.
 *   <li>Abono del destino aceptado: el pago queda completado. Rechazado: se devuelve lo descontado
 *       al origen (al monedero, o se pide devolver el cargo a su cuenta) y el pago queda rechazado.
 *   <li>Transferencia del saldo al asociar una tarjeta aceptada: la tarjeta queda asociada.
 *       Rechazada: el saldo vuelve al monedero.
 * </ul>
 *
 * <p>Una respuesta que no corresponde al paso actual (por ejemplo, repetida) se ignora. Es
 * reactivo: no hace nada hasta que se suscribe al {@link Completable}.
 */
public interface HandleOperationResultUseCase {

    /**
     * Aplica la respuesta.
     *
     * @param operationId id de la operación
     * @param completed   true si la cuenta la aceptó
     * @param reason      motivo del rechazo (null si se aceptó)
     */
    Completable execute(String operationId, boolean completed, String reason);
}

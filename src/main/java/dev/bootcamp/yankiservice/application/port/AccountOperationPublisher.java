package dev.bootcamp.yankiservice.application.port;

import io.reactivex.rxjava3.core.Completable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pedidos a transaction-service para cargar o abonar una cuenta bancaria. La respuesta llega como
 * evento con el mismo id de operación.
 */
public interface AccountOperationPublisher {

    /**
     * Pide cargar el monto a la cuenta (se trata como un retiro).
     *
     * @return termina cuando se publicó el pedido; error si no se pudo
     */
    Completable requestDebit(
            String operationId,
            String accountId,
            BigDecimal amount,
            LocalDate date);

    /**
     * Pide abonar el monto a la cuenta (se trata como un depósito).
     *
     * @param reversal true si devuelve un cargo anterior: se abona sin reglas ni comisión
     * @return termina cuando se publicó el pedido; error si no se pudo
     */
    Completable requestCredit(
            String operationId,
            String accountId,
            BigDecimal amount,
            LocalDate date,
            boolean reversal);
}

package dev.bootcamp.yankiservice.application.port;

import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de persistencia de los pagos Yanki.
 */
public interface YankiPaymentRepository {

    /**
     * Guarda el pago y lo emite con su id asignado.
     */
    Single<YankiPayment> save(YankiPayment payment);

    /**
     * Busca el pago por su id.
     */
    Maybe<YankiPayment> findById(String paymentId);

    /**
     * Pagos enviados o recibidos por el monedero.
     */
    Flowable<YankiPayment> findByWalletId(String walletId);
}

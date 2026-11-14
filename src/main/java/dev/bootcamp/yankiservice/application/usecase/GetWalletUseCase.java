package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Caso de uso para que el dueño de un monedero consulte su monedero, sus pagos y las tarjetas que
 * le quieren asociar.
 *
 * <p>Es reactivo: no hace nada hasta que se suscribe.
 */
public interface GetWalletUseCase {

    /**
     * Consulta el monedero.
     *
     * @return el monedero; error {@link NotFoundException} si no existe o fue dado de baja
     */
    Single<Wallet> findWallet(String walletId);

    /**
     * Pagos enviados o recibidos por el monedero.
     */
    Flowable<YankiPayment> findPayments(String walletId);

    /**
     * Consulta un pago enviado o recibido por el monedero.
     *
     * @return el pago; error {@link NotFoundException} si no existe o el monedero no participa
     */
    Single<YankiPayment> findPayment(String walletId, String paymentId);

    /**
     * Asociaciones de tarjetas dirigidas al monedero (para confirmarlas).
     */
    Flowable<DebitCardLink> findDebitCardLinks(String walletId);
}

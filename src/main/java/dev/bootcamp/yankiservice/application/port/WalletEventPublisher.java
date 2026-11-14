package dev.bootcamp.yankiservice.application.port;

import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Completable;

/**
 * Publicación de los eventos de monederos (auth-service los usa para registrar el acceso).
 */
public interface WalletEventPublisher {

    /**
     * Publica que se creó un monedero.
     *
     * @return termina cuando se publicó, o sin error si no se pudo (el adaptador lo registra)
     */
    Completable publishWalletCreated(Wallet wallet);

    /**
     * Publica que se dio de baja un monedero.
     *
     * @return termina cuando se publicó, o sin error si no se pudo (el adaptador lo registra)
     */
    Completable publishWalletClosed(Wallet wallet);
}

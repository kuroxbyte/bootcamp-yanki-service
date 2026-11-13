package dev.bootcamp.yankiservice.application.port;

import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de persistencia de las asociaciones de tarjetas de débito con monederos.
 */
public interface DebitCardLinkRepository {

    /**
     * Guarda la asociación y la emite con su id asignado.
     */
    Single<DebitCardLink> save(DebitCardLink link);

    /**
     * Busca la asociación por su id.
     */
    Maybe<DebitCardLink> findById(String linkId);

    /**
     * Asociaciones dirigidas al monedero.
     */
    Flowable<DebitCardLink> findByWalletId(String walletId);
}

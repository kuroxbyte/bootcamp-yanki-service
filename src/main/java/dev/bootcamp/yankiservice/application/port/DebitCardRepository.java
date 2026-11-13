package dev.bootcamp.yankiservice.application.port;

import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Copia local de las tarjetas de débito del banco (datos maestros), con caché.
 */
public interface DebitCardRepository {

    /**
     * Guarda o reemplaza la tarjeta.
     */
    Completable save(DebitCard debitCard);

    /**
     * Busca la tarjeta por su id.
     *
     * @return la tarjeta, o vacío si yanki-service todavía no la conoce
     */
    Maybe<DebitCard> findById(String debitCardId);
}

package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import io.reactivex.rxjava3.core.Completable;

/**
 * Caso de uso para guardar en la copia local una tarjeta de débito emitida o actualizada en
 * account-service. Cada evento trae el estado completo de la tarjeta, así que es idempotente. Es
 * reactivo: no hace nada hasta que se suscribe al {@link Completable}.
 */
public interface SaveDebitCardUseCase {

    /**
     * Guarda o reemplaza la tarjeta.
     */
    Completable execute(DebitCard debitCard);
}

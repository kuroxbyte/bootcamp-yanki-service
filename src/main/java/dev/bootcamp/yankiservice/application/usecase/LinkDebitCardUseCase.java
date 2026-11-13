package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import io.reactivex.rxjava3.core.Single;

/**
 * Caso de uso para asociar una tarjeta de débito del banco a un monedero, en dos pasos.
 *
 * <ol>
 *   <li>El cliente dueño de la tarjeta la autoriza para el monedero de un celular. La tarjeta debe
 *       ser del cliente y no estar asociada a otro monedero; el monedero no debe tener tarjeta.
 *   <li>El dueño del monedero confirma. Si el monedero tiene saldo, se transfiere a la cuenta
 *       principal de la tarjeta (se trata como un depósito); la tarjeta queda asociada cuando la
 *       cuenta lo acepta ({@code ACTIVE}). Si lo rechaza, el monedero queda como estaba
 *       ({@code REJECTED}). Con saldo 0 la tarjeta queda asociada en el momento.
 * </ol>
 *
 * <p>Desde entonces, los pagos del monedero se cargan y abonan en la cuenta principal de la
 * tarjeta. Es reactivo: no hace nada hasta que se suscribe al {@link Single}.
 */
public interface LinkDebitCardUseCase {

    /**
     * El cliente autoriza su tarjeta para el monedero del celular.
     *
     * @return la asociación pendiente de confirmación; error {@link NotFoundException} si la
     *     tarjeta no es del cliente o el celular no tiene monedero; {@link BusinessRuleException}
     *     si la tarjeta ya está en un monedero o el monedero ya tiene tarjeta
     */
    Single<DebitCardLink> authorize(String customerId, String phone, String debitCardId);

    /**
     * El dueño del monedero confirma la asociación.
     *
     * @return la asociación, {@code ACTIVE} o {@code TRANSFERRING}; error {@link
     *     NotFoundException} si no existe o es de otro monedero; {@link BusinessRuleException} si
     *     ya no está pendiente o el monedero no está disponible; {@link
     *     OperationUnavailableException} si no se pudo pedir la transferencia del saldo
     */
    Single<DebitCardLink> confirm(String walletId, String linkId);
}

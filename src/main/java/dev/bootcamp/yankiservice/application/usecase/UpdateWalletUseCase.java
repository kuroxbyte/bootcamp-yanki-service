package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Casos de uso del dueño sobre su monedero: cambiar sus datos de contacto y darlo de baja.
 *
 * <p>Son reactivos: no hacen nada hasta que se suscribe.
 */
public interface UpdateWalletUseCase {

    /**
     * Cambia el IMEI (por ejemplo, al cambiar de celular) y el correo. El documento y el número de
     * celular no cambian.
     *
     * @return el monedero modificado; error {@link NotFoundException} si no existe o fue dado de
     *     baja, o {@link BusinessRuleException} si los datos no son válidos
     */
    Single<Wallet> update(String walletId, String imei, String email);

    /**
     * Da de baja el monedero (baja lógica, estado {@code CLOSED}). Solo si está activo, sin pagos
     * pendientes y, si usa su propio saldo, con saldo 0. Libera su tarjeta de débito y su
     * celular, y publica {@code WalletClosed} para que auth-service elimine su acceso.
     *
     * @return termina sin error si se dio de baja; error {@link NotFoundException} si no existe o
     *     ya fue dado de baja, o {@link BusinessRuleException} si no se puede
     */
    Completable close(String walletId);
}

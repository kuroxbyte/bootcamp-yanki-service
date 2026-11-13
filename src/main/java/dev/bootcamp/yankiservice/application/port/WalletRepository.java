package dev.bootcamp.yankiservice.application.port;

import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.math.BigDecimal;

/**
 * Puerto de persistencia de los monederos. Las operaciones de saldo son atómicas: dos pagos a la
 * vez no dejan un saldo negativo.
 */
public interface WalletRepository {

    /**
     * Guarda el monedero y lo emite con su id asignado.
     */
    Single<Wallet> save(Wallet wallet);

    /**
     * Busca el monedero por su id.
     */
    Maybe<Wallet> findById(String walletId);

    /**
     * Busca el monedero por su celular.
     */
    Maybe<Wallet> findByPhone(String phone);

    /**
     * Indica si la tarjeta de débito ya está asociada a algún monedero.
     */
    Single<Boolean> existsByDebitCardId(String debitCardId);

    /**
     * Descuenta el monto del saldo si el monedero está activo, no tiene tarjeta y el saldo
     * alcanza.
     *
     * @return true si se descontó; false si no se cumplían las condiciones
     */
    Single<Boolean> debitBalance(String walletId, BigDecimal amount);

    /**
     * Suma el monto al saldo del monedero.
     */
    Completable creditBalance(String walletId, BigDecimal amount);

    /**
     * Empieza a asociar una tarjeta: si el monedero está activo y sin tarjeta, lo pasa a
     * {@code LINKING} con saldo 0.
     *
     * @return el saldo que tenía, o vacío si el monedero no estaba disponible
     */
    Maybe<BigDecimal> startLinking(String walletId);

    /**
     * Termina la asociación: deja la tarjeta en el monedero y lo vuelve a activar.
     */
    Completable finishLinking(String walletId, String debitCardId);

    /**
     * Cancela la asociación: devuelve el saldo al monedero y lo vuelve a activar sin tarjeta.
     */
    Completable cancelLinking(String walletId, BigDecimal balance);
}

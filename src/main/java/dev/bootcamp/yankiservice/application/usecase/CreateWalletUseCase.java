package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Single;

/**
 * Caso de uso para crear un monedero Yanki. No hace falta ser cliente del banco.
 *
 * <p>Reglas: el documento, el celular (9 dígitos, empieza con 9), el IMEI (15 dígitos con dígito
 * de control) y el correo deben ser válidos; hay un solo monedero por celular. El monedero empieza
 * activo, sin tarjeta y con saldo 0. Al crearlo publica {@code WalletCreated}.
 *
 * <p>Es reactivo: no hace nada hasta que se suscribe al {@link Single}.
 */
public interface CreateWalletUseCase {

    /**
     * Crea el monedero.
     *
     * @return el monedero guardado; error {@link BusinessRuleException} si un dato no es válido o
     *     el celular ya tiene un monedero
     */
    Single<Wallet> execute(
            DocumentType documentType,
            String documentNumber,
            String phone,
            String imei,
            String email);
}

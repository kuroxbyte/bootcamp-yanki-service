package dev.bootcamp.yankiservice.domain.model.link;

/**
 * Estado de la asociación de una tarjeta de débito con un monedero.
 */
public enum LinkStatus {
    /**
     * El cliente autorizó su tarjeta; falta que el dueño del monedero lo confirme.
     */
    PENDING_CONFIRMATION,
    /**
     * Confirmada; el saldo del monedero se está transfiriendo a la cuenta principal.
     */
    TRANSFERRING,
    /**
     * La tarjeta quedó asociada al monedero.
     */
    ACTIVE,
    /**
     * La transferencia del saldo fue rechazada; el monedero quedó como estaba.
     */
    REJECTED
}

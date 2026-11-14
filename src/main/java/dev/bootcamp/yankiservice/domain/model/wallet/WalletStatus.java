package dev.bootcamp.yankiservice.domain.model.wallet;

/**
 * Estado de un monedero.
 */
public enum WalletStatus {
    /**
     * Puede enviar y recibir pagos.
     */
    ACTIVE,
    /**
     * Su saldo se está transfiriendo a la cuenta principal de la tarjeta que se le asocia; no
     * envía ni recibe pagos hasta que termine.
     */
    LINKING,
    /**
     * Dado de baja por su dueño: ya no envía ni recibe pagos ni aparece al buscarlo.
     */
    CLOSED
}

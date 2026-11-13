package dev.bootcamp.yankiservice.domain.model.payment;

/**
 * Paso en el que está un pago pendiente; un resultado de transaction-service solo se aplica si
 * corresponde al paso actual (así un evento repetido no se procesa dos veces).
 */
public enum PaymentStep {
    /**
     * Se espera el cargo en la cuenta del origen.
     */
    DEBITING_SOURCE,
    /**
     * Se espera el abono en la cuenta del destino.
     */
    CREDITING_TARGET,
    /**
     * El abono fue rechazado; se espera la devolución del cargo a la cuenta del origen.
     */
    REFUNDING_SOURCE,
    /**
     * El pago terminó (completado o rechazado).
     */
    DONE
}

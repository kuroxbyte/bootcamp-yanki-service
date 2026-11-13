package dev.bootcamp.yankiservice.domain.model.payment;

/**
 * Estado de un pago Yanki.
 */
public enum PaymentStatus {
    /**
     * En proceso: espera la respuesta de transaction-service sobre una cuenta bancaria.
     */
    PENDING,
    /**
     * El destino recibió el pago.
     */
    COMPLETED,
    /**
     * No se hizo; si algo se había descontado, se devolvió.
     */
    REJECTED
}

package dev.bootcamp.yankiservice.domain.exception;

/**
 * Se incumple una regla de negocio. Se traduce a una respuesta 422.
 */
public class BusinessRuleException extends RuntimeException {
    /**
     * Crea la excepción con el mensaje para el cliente.
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}

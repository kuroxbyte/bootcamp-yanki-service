package dev.bootcamp.yankiservice.domain.exception;

/**
 * No se pudo pedir la operación a la cuenta bancaria (Kafka no disponible). Lo que se había
 * descontado ya se devolvió. Se traduce a una respuesta 503.
 */
public class OperationUnavailableException extends RuntimeException {
    /**
     * Crea la excepción con el mensaje para el cliente y la causa.
     */
    public OperationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

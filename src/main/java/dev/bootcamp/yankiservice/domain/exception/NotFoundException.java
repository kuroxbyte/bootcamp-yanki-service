package dev.bootcamp.yankiservice.domain.exception;

/**
 * El recurso no existe o no pertenece a quien lo pide. Se traduce a una respuesta 404.
 */
public class NotFoundException extends RuntimeException {
    /**
     * Crea la excepción con el mensaje para el cliente.
     */
    public NotFoundException(String message) {
        super(message);
    }
}

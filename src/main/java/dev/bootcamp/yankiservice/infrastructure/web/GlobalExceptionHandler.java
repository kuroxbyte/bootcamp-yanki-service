package dev.bootcamp.yankiservice.infrastructure.web;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Traduce las excepciones a respuestas Problem Details (RFC 7807).
 *
 * <ul>
 *   <li>400: datos de entrada inválidos; lo resuelve {@link ResponseEntityExceptionHandler}.
 *   <li>404: {@link NotFoundException}.
 *   <li>422: {@link BusinessRuleException}, o un dato duplicado que detecta un índice único de
 *       MongoDB ({@link DuplicateKeyException}) cuando dos pedidos llegan a la vez.
 *   <li>503: {@link OperationUnavailableException}.
 *   <li>500: cualquier otro error inesperado.
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String BUSINESS_RULE = "Regla de negocio incumplida";

    /**
     * Responde 404 cuando el recurso no existe.
     */
    @ExceptionHandler(NotFoundException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    /**
     * Responde 422 cuando se incumple una regla de negocio.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violated: {}", ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, BUSINESS_RULE, ex.getMessage());
    }

    /**
     * Responde 422 cuando un índice único rechaza un dato duplicado.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleDuplicate(DuplicateKeyException ex) {
        log.warn("Duplicate key: {}", ex.getMessage());
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                BUSINESS_RULE,
                "El celular ya tiene un monedero o la tarjeta ya está asociada a un monedero");
    }

    /**
     * Responde 503 cuando no se pudo pedir la operación a la cuenta bancaria.
     */
    @ExceptionHandler(OperationUnavailableException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleUnavailable(
            OperationUnavailableException ex) {
        log.error("Operation unavailable: {}", ex.getMessage(), ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", ex.getMessage());
    }

    /**
     * Responde 500 ante cualquier otro error, sin exponer el detalle interno.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ProblemDetail>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno",
                "Ocurrió un error inesperado");
    }

    private static Mono<ResponseEntity<ProblemDetail>> problem(
            HttpStatus status,
            String title,
            String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return Mono.just(ResponseEntity.status(status).body(problem));
    }
}

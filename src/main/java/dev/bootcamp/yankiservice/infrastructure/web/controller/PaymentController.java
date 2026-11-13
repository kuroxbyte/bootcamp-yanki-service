package dev.bootcamp.yankiservice.infrastructure.web.controller;

import dev.bootcamp.yankiservice.application.usecase.GetWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.SendPaymentUseCase;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStatus;
import dev.bootcamp.yankiservice.infrastructure.web.api.PaymentsApi;
import dev.bootcamp.yankiservice.infrastructure.web.dto.PaymentRequestDto;
import dev.bootcamp.yankiservice.infrastructure.web.dto.PaymentResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Endpoints de pagos definidos en el contrato OpenAPI. Un pago completado en el momento responde
 * 201; uno que espera a una cuenta bancaria, 202.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

    private final SendPaymentUseCase sendPaymentUseCase;
    private final GetWalletUseCase getWalletUseCase;
    private final Clock clock;

    @Override
    public Mono<ResponseEntity<PaymentResponseDto>> sendPayment(
            String xwalletId,
            Mono<PaymentRequestDto> paymentRequestDto,
            ServerWebExchange exchange) {
        return paymentRequestDto
                .flatMap(request -> Mono.from(sendPaymentUseCase
                        .execute(
                                xwalletId,
                                request.getToPhone(),
                                request.getAmount(),
                                LocalDate.now(clock))
                        .toFlowable()))
                .doOnNext(payment -> log.info(
                        "Payment sent paymentId={} status={}",
                        payment.getId(),
                        payment.getStatus()))
                .map(payment -> ResponseEntity
                        .status(payment.getStatus() == PaymentStatus.COMPLETED
                                ? HttpStatus.CREATED
                                : HttpStatus.ACCEPTED)
                        .body(YankiWebMapper.toDto(payment)));
    }

    @Override
    public Mono<ResponseEntity<Flux<PaymentResponseDto>>> listPayments(
            String xwalletId,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(Flux.from(getWalletUseCase
                .findPayments(xwalletId)
                .map(YankiWebMapper::toDto))));
    }

    @Override
    public Mono<ResponseEntity<PaymentResponseDto>> getPayment(
            String xwalletId,
            String paymentId,
            ServerWebExchange exchange) {
        return Mono.from(getWalletUseCase.findPayment(xwalletId, paymentId).toFlowable())
                .map(payment -> ResponseEntity.ok(YankiWebMapper.toDto(payment)));
    }
}

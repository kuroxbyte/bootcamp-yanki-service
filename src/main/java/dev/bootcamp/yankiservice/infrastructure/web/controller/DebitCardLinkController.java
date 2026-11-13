package dev.bootcamp.yankiservice.infrastructure.web.controller;

import dev.bootcamp.yankiservice.application.usecase.GetWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.LinkDebitCardUseCase;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import dev.bootcamp.yankiservice.infrastructure.web.api.DebitCardLinksApi;
import dev.bootcamp.yankiservice.infrastructure.web.dto.DebitCardLinkRequestDto;
import dev.bootcamp.yankiservice.infrastructure.web.dto.DebitCardLinkResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Endpoints de asociación de tarjetas de débito definidos en el contrato OpenAPI.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DebitCardLinkController implements DebitCardLinksApi {

    private final LinkDebitCardUseCase linkDebitCardUseCase;
    private final GetWalletUseCase getWalletUseCase;

    @Override
    public Mono<ResponseEntity<DebitCardLinkResponseDto>> authorizeDebitCardLink(
            String xcustomerId,
            Mono<DebitCardLinkRequestDto> debitCardLinkRequestDto,
            ServerWebExchange exchange) {
        return debitCardLinkRequestDto
                .flatMap(request -> Mono.from(linkDebitCardUseCase
                        .authorize(xcustomerId, request.getPhone(), request.getDebitCardId())
                        .toFlowable()))
                .doOnNext(link -> log.info(
                        "Debit card link authorized linkId={} debitCardId={}",
                        link.getId(),
                        link.getDebitCardId()))
                .map(link -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(YankiWebMapper.toDto(link)));
    }

    @Override
    public Mono<ResponseEntity<DebitCardLinkResponseDto>> confirmDebitCardLink(
            String xwalletId,
            String linkId,
            ServerWebExchange exchange) {
        return Mono.from(linkDebitCardUseCase.confirm(xwalletId, linkId).toFlowable())
                .doOnNext(link -> log.info(
                        "Debit card link confirmed linkId={} status={}",
                        link.getId(),
                        link.getStatus()))
                .map(link -> ResponseEntity
                        .status(link.getStatus() == LinkStatus.ACTIVE
                                ? HttpStatus.OK
                                : HttpStatus.ACCEPTED)
                        .body(YankiWebMapper.toDto(link)));
    }

    @Override
    public Mono<ResponseEntity<Flux<DebitCardLinkResponseDto>>> listDebitCardLinks(
            String xwalletId,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(Flux.from(getWalletUseCase
                .findDebitCardLinks(xwalletId)
                .map(YankiWebMapper::toDto))));
    }
}

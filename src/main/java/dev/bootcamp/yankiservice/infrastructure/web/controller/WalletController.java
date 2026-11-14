package dev.bootcamp.yankiservice.infrastructure.web.controller;

import dev.bootcamp.yankiservice.application.usecase.CreateWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.GetWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.UpdateWalletUseCase;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.infrastructure.web.api.WalletsApi;
import dev.bootcamp.yankiservice.infrastructure.web.dto.WalletRequestDto;
import dev.bootcamp.yankiservice.infrastructure.web.dto.WalletResponseDto;
import dev.bootcamp.yankiservice.infrastructure.web.dto.WalletUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Endpoints de monederos definidos en el contrato OpenAPI.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WalletController implements WalletsApi {

    private final CreateWalletUseCase createWalletUseCase;
    private final GetWalletUseCase getWalletUseCase;
    private final UpdateWalletUseCase updateWalletUseCase;

    @Override
    public Mono<ResponseEntity<WalletResponseDto>> createWallet(
            Mono<WalletRequestDto> walletRequestDto,
            ServerWebExchange exchange) {
        return walletRequestDto
                .flatMap(request -> Mono.from(createWalletUseCase
                        .execute(
                                DocumentType.valueOf(request.getDocumentType().getValue()),
                                request.getDocumentNumber(),
                                request.getPhone(),
                                request.getImei(),
                                request.getEmail())
                        .toFlowable()))
                .doOnNext(wallet -> log.info("Wallet created walletId={}", wallet.getId()))
                .map(wallet -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(YankiWebMapper.toDto(wallet)));
    }

    @Override
    public Mono<ResponseEntity<WalletResponseDto>> getWallet(
            String xwalletId,
            ServerWebExchange exchange) {
        return Mono.from(getWalletUseCase.findWallet(xwalletId).toFlowable())
                .map(wallet -> ResponseEntity.ok(YankiWebMapper.toDto(wallet)));
    }

    @Override
    public Mono<ResponseEntity<WalletResponseDto>> updateWallet(
            String walletId,
            Mono<WalletUpdateRequestDto> walletUpdateRequestDto,
            ServerWebExchange exchange) {
        return walletUpdateRequestDto
                .flatMap(request -> Mono.from(updateWalletUseCase
                        .update(walletId, request.getImei(), request.getEmail())
                        .toFlowable()))
                .doOnNext(wallet -> log.info("Wallet updated walletId={}", walletId))
                .map(wallet -> ResponseEntity.ok(YankiWebMapper.toDto(wallet)));
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteWallet(String walletId, ServerWebExchange exchange) {
        return Mono.from(updateWalletUseCase.close(walletId).toFlowable())
                .then(Mono.fromCallable(() -> {
                    log.info("Wallet closed walletId={}", walletId);
                    return ResponseEntity.noContent().<Void>build();
                }));
    }
}

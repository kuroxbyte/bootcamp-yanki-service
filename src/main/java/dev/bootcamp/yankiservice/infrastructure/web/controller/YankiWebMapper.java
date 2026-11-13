package dev.bootcamp.yankiservice.infrastructure.web.controller;

import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.infrastructure.web.dto.DebitCardLinkResponseDto;
import dev.bootcamp.yankiservice.infrastructure.web.dto.PaymentResponseDto;
import dev.bootcamp.yankiservice.infrastructure.web.dto.WalletResponseDto;

/**
 * Convierte los objetos de dominio en las respuestas del contrato OpenAPI.
 */
final class YankiWebMapper {

    private YankiWebMapper() {}

    static WalletResponseDto toDto(Wallet wallet) {
        return new WalletResponseDto()
                .id(wallet.getId())
                .documentType(WalletResponseDto.DocumentTypeEnum.fromValue(
                        wallet.getDocumentType().name()))
                .documentNumber(wallet.getDocumentNumber())
                .phone(wallet.getPhone())
                .imei(wallet.getImei())
                .email(wallet.getEmail())
                .balance(wallet.getBalance())
                .debitCardId(wallet.getDebitCardId())
                .status(WalletResponseDto.StatusEnum.fromValue(wallet.getStatus().name()))
                .createdAt(wallet.getCreatedAt());
    }

    static PaymentResponseDto toDto(YankiPayment payment) {
        return new PaymentResponseDto()
                .id(payment.getId())
                .fromPhone(payment.getSourcePhone())
                .toPhone(payment.getTargetPhone())
                .amount(payment.getAmount())
                .date(payment.getDate())
                .status(PaymentResponseDto.StatusEnum.fromValue(payment.getStatus().name()))
                .rejectionReason(payment.getRejectionReason());
    }

    static DebitCardLinkResponseDto toDto(DebitCardLink link) {
        return new DebitCardLinkResponseDto()
                .id(link.getId())
                .debitCardId(link.getDebitCardId())
                .phone(link.getPhone())
                .requestedAt(link.getRequestedAt())
                .status(DebitCardLinkResponseDto.StatusEnum.fromValue(link.getStatus().name()))
                .transferredBalance(link.getTransferredBalance())
                .rejectionReason(link.getRejectionReason());
    }
}

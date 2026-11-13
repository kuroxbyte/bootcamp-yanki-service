package dev.bootcamp.yankiservice.infrastructure.web.controller;

import dev.bootcamp.yankiservice.application.usecase.CreateWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.GetWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.LinkDebitCardUseCase;
import dev.bootcamp.yankiservice.application.usecase.SendPaymentUseCase;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest({WalletController.class, PaymentController.class, DebitCardLinkController.class})
@Import(YankiControllersTest.FixedClock.class)
class YankiControllersTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);
    private static final String WALLET = "{\"documentType\":\"DNI\","
            + "\"documentNumber\":\"12345678\","
            + "\"phone\":\"987654321\",\"imei\":\"490154203237518\",\"email\":\"a@b.pe\"}";

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private CreateWalletUseCase createWalletUseCase;

    @MockitoBean private GetWalletUseCase getWalletUseCase;

    @MockitoBean private SendPaymentUseCase sendPaymentUseCase;

    @MockitoBean private LinkDebitCardUseCase linkDebitCardUseCase;

    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(DATE.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    private static Wallet wallet() {
        Wallet wallet = new Wallet(
                DocumentType.DNI, "12345678", "987654321", "490154203237518", "a@b.pe");
        wallet.setId("w1");
        wallet.setCreatedAt(DATE);
        return wallet;
    }

    private static YankiPayment payment() {
        YankiPayment payment = new YankiPayment(
                "w1", "987654321", null, "w2", "912345678", null, new BigDecimal("30"), DATE);
        payment.setId("p1");
        return payment;
    }

    private static DebitCardLink link() {
        DebitCardLink link = new DebitCardLink("c1", "d1", "w1", "987654321", DATE);
        link.setId("l1");
        return link;
    }

    private WebTestClient.ResponseSpec post(String path, String header, String id, String body) {
        return webTestClient.post()
                .uri(path)
                .header(header, id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    @Test
    void createsWallet() {
        when(createWalletUseCase.execute(
                DocumentType.DNI, "12345678", "987654321", "490154203237518", "a@b.pe"))
                .thenReturn(Single.just(wallet()));

        webTestClient.post().uri("/api/v1/yanki/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(WALLET)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("w1")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.balance").isEqualTo(0);
    }

    @Test
    void rejectsInvalidOrDuplicatedWallet() {
        when(createWalletUseCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(Single.error(new BusinessRuleException("El IMEI no es válido")))
                .thenReturn(Single.error(new DuplicateKeyException("phone")));

        webTestClient.post().uri("/api/v1/yanki/wallets")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(WALLET).exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.detail").isEqualTo("El IMEI no es válido");
        webTestClient.post().uri("/api/v1/yanki/wallets")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(WALLET).exchange()
                .expectStatus().isEqualTo(422);
        webTestClient.post().uri("/api/v1/yanki/wallets")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"phone\":\"9\"}").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getsOwnWalletOrNotFound() {
        when(getWalletUseCase.findWallet("w1")).thenReturn(Single.just(wallet()));
        when(getWalletUseCase.findWallet("w9"))
                .thenReturn(Single.error(new NotFoundException("El monedero no existe")));

        webTestClient.get().uri("/api/v1/yanki/wallet").header("X-Wallet-Id", "w1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.phone").isEqualTo("987654321");
        webTestClient.get().uri("/api/v1/yanki/wallet").header("X-Wallet-Id", "w9").exchange()
                .expectStatus().isNotFound();
        webTestClient.get().uri("/api/v1/yanki/wallet").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void completedPaymentAnswers201AndPendingPayment202() {
        YankiPayment completed = payment();
        completed.complete();
        when(sendPaymentUseCase.execute("w1", "912345678", new BigDecimal("30"), DATE))
                .thenReturn(Single.just(completed))
                .thenReturn(Single.just(payment()));
        String body = "{\"toPhone\":\"912345678\",\"amount\":30}";

        post("/api/v1/yanki/payments", "X-Wallet-Id", "w1", body)
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.fromPhone").isEqualTo("987654321");
        post("/api/v1/yanki/payments", "X-Wallet-Id", "w1", body)
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void paymentErrorsAreTranslated() {
        when(sendPaymentUseCase.execute(eq("w1"), any(), any(), any()))
                .thenReturn(Single.error(new BusinessRuleException("Saldo insuficiente")))
                .thenReturn(Single.error(new OperationUnavailableException(
                        "No se pudo", new IllegalStateException())))
                .thenReturn(Single.error(new IllegalStateException("boom")));
        String body = "{\"toPhone\":\"912345678\",\"amount\":30}";

        post("/api/v1/yanki/payments", "X-Wallet-Id", "w1", body)
                .expectStatus().isEqualTo(422);
        post("/api/v1/yanki/payments", "X-Wallet-Id", "w1", body)
                .expectStatus().isEqualTo(503);
        post("/api/v1/yanki/payments", "X-Wallet-Id", "w1", body)
                .expectStatus().isEqualTo(500);
    }

    @Test
    void listsAndGetsPayments() {
        when(getWalletUseCase.findPayments("w1")).thenReturn(Flowable.just(payment()));
        when(getWalletUseCase.findPayment("w1", "p1")).thenReturn(Single.just(payment()));

        webTestClient.get().uri("/api/v1/yanki/payments").header("X-Wallet-Id", "w1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo("p1");
        webTestClient.get().uri("/api/v1/yanki/payments/p1").header("X-Wallet-Id", "w1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.amount").isEqualTo(30);
    }

    @Test
    void customerAuthorizesLinkAndWalletListsAndConfirmsIt() {
        DebitCardLink active = link();
        active.setStatus(LinkStatus.ACTIVE);
        DebitCardLink transferring = link();
        transferring.setStatus(LinkStatus.TRANSFERRING);
        when(linkDebitCardUseCase.authorize("c1", "987654321", "d1"))
                .thenReturn(Single.just(link()));
        when(getWalletUseCase.findDebitCardLinks("w1")).thenReturn(Flowable.just(link()));
        when(linkDebitCardUseCase.confirm("w1", "l1"))
                .thenReturn(Single.just(active))
                .thenReturn(Single.just(transferring));

        post("/api/v1/yanki/debit-card-links", "X-Customer-Id", "c1",
                "{\"phone\":\"987654321\",\"debitCardId\":\"d1\"}")
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING_CONFIRMATION");
        webTestClient.get().uri("/api/v1/yanki/debit-card-links").header("X-Wallet-Id", "w1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo("l1");
        post("/api/v1/yanki/debit-card-links/l1/confirm", "X-Wallet-Id", "w1", "{}")
                .expectStatus().isOk();
        post("/api/v1/yanki/debit-card-links/l1/confirm", "X-Wallet-Id", "w1", "{}")
                .expectStatus().isAccepted();
    }
}

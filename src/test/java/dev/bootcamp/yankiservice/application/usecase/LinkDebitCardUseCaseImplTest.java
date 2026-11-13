package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkDebitCardUseCaseImplTest {

    private static final String PHONE = "987654321";
    private static final DebitCard CARD = new DebitCard("d1", "c1", "a1");

    @Mock private DebitCardRepository debitCardRepository;

    @Mock private WalletRepository walletRepository;

    @Mock private DebitCardLinkRepository linkRepository;

    @Mock private AccountOperationPublisher operationPublisher;

    private LinkDebitCardUseCaseImpl useCase;

    @BeforeEach
    void createUseCase() {
        Clock clock = Clock.fixed(
                Fixtures.DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        useCase = new LinkDebitCardUseCaseImpl(
                debitCardRepository,
                walletRepository,
                linkRepository,
                operationPublisher,
                clock);
    }

    private void savesLinks() {
        when(linkRepository.save(any())).thenAnswer(invocation -> {
            DebitCardLink link = invocation.getArgument(0);
            if (link.getId() == null) {
                link.setId("l1");
            }
            return Single.just(link);
        });
    }

    private DebitCardLink pendingLink() {
        DebitCardLink link = Fixtures.link("l1");
        when(linkRepository.findById("l1")).thenReturn(Maybe.just(link));
        return link;
    }

    @Test
    void customerAuthorizesOwnFreeCardForWalletWithoutCard() {
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(walletRepository.existsByDebitCardId("d1")).thenReturn(Single.just(false));
        when(walletRepository.findByPhone(PHONE))
                .thenReturn(Maybe.just(Fixtures.wallet("w1", PHONE, null)));
        savesLinks();

        useCase.authorize("c1", PHONE, "d1").test().assertValue(link -> link.getId().equals("l1")
                && link.getWalletId().equals("w1")
                && link.getStatus() == LinkStatus.PENDING_CONFIRMATION);
    }

    @Test
    void cardMustExistAndBelongToTheCustomer() {
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(debitCardRepository.findById("d9")).thenReturn(Maybe.empty());

        useCase.authorize("c2", PHONE, "d1").test().assertError(NotFoundException.class);
        useCase.authorize("c1", PHONE, "d9").test().assertError(NotFoundException.class);
        verifyNoInteractions(walletRepository, linkRepository);
    }

    @Test
    void cardAlreadyLinkedIsRejected() {
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(walletRepository.existsByDebitCardId("d1")).thenReturn(Single.just(true));

        useCase.authorize("c1", PHONE, "d1").test().assertError(BusinessRuleException.class);
        verify(walletRepository, never()).findByPhone(any());
    }

    @Test
    void phoneMustHaveWalletWithoutCard() {
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(walletRepository.existsByDebitCardId("d1")).thenReturn(Single.just(false));
        when(walletRepository.findByPhone(PHONE))
                .thenReturn(Maybe.empty())
                .thenReturn(Maybe.just(Fixtures.wallet("w1", PHONE, "d7")));

        useCase.authorize("c1", PHONE, "d1").test().assertError(NotFoundException.class);
        useCase.authorize("c1", PHONE, "d1").test().assertError(BusinessRuleException.class);
        verifyNoInteractions(linkRepository);
    }

    @Test
    void confirmingWithZeroBalanceLinksAtOnce() {
        pendingLink();
        when(walletRepository.existsByDebitCardId("d1")).thenReturn(Single.just(false));
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(walletRepository.startLinking("w1")).thenReturn(Maybe.just(BigDecimal.ZERO));
        when(walletRepository.finishLinking("w1", "d1")).thenReturn(Completable.complete());
        savesLinks();

        useCase.confirm("w1", "l1").test()
                .assertValue(link -> link.getStatus() == LinkStatus.ACTIVE);
        verifyNoInteractions(operationPublisher);
    }

    @Test
    void confirmingWithBalanceRequestsTheTransferToThePrimaryAccount() {
        pendingLink();
        when(walletRepository.existsByDebitCardId("d1")).thenReturn(Single.just(false));
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(walletRepository.startLinking("w1")).thenReturn(Maybe.just(new BigDecimal("100")));
        savesLinks();
        when(operationPublisher.requestCredit(
                "link:l1:transfer", "a1", new BigDecimal("100"), Fixtures.DATE, false))
                .thenReturn(Completable.complete());

        useCase.confirm("w1", "l1").test()
                .assertValue(link -> link.getStatus() == LinkStatus.TRANSFERRING);
        verify(walletRepository, never()).finishLinking(any(), any());
    }

    @Test
    void confirmingRestoresTheWalletWhenTheTransferCannotBeRequested() {
        final DebitCardLink link = pendingLink();
        when(walletRepository.existsByDebitCardId("d1")).thenReturn(Single.just(false));
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.just(CARD));
        when(walletRepository.startLinking("w1")).thenReturn(Maybe.just(new BigDecimal("100")));
        savesLinks();
        when(operationPublisher.requestCredit(
                "link:l1:transfer", "a1", new BigDecimal("100"), Fixtures.DATE, false))
                .thenReturn(Completable.error(new IllegalStateException("kafka down")));
        when(walletRepository.cancelLinking("w1", new BigDecimal("100")))
                .thenReturn(Completable.complete());

        useCase.confirm("w1", "l1").test().assertError(OperationUnavailableException.class);
        assertThat(link.getStatus())
                .isEqualTo(LinkStatus.REJECTED);
    }

    @Test
    void onlyTheTargetWalletConfirmsPendingLink() {
        DebitCardLink link = pendingLink();

        useCase.confirm("w2", "l1").test().assertError(NotFoundException.class);
        link.setStatus(LinkStatus.ACTIVE);
        useCase.confirm("w1", "l1").test().assertError(BusinessRuleException.class);
        verifyNoInteractions(walletRepository);
    }

    @Test
    void confirmFailsWhenCardWasLinkedMeanwhileOrWalletIsBusy() {
        pendingLink();
        when(walletRepository.existsByDebitCardId("d1"))
                .thenReturn(Single.just(true))
                .thenReturn(Single.just(false))
                .thenReturn(Single.just(false));
        when(debitCardRepository.findById("d1"))
                .thenReturn(Maybe.empty())
                .thenReturn(Maybe.just(CARD));
        when(walletRepository.startLinking("w1")).thenReturn(Maybe.empty());

        useCase.confirm("w1", "l1").test().assertError(BusinessRuleException.class);
        useCase.confirm("w1", "l1").test().assertError(BusinessRuleException.class);
        useCase.confirm("w1", "l1").test().assertError(BusinessRuleException.class);
        verify(linkRepository, never()).save(any());
    }
}

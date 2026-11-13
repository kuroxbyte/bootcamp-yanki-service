package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveDebitCardUseCaseImplTest {

    @Mock private DebitCardRepository debitCardRepository;

    @Test
    void savesTheCardInTheLocalCopy() {
        DebitCard card = new DebitCard("d1", "c1", "a1");
        when(debitCardRepository.save(card)).thenReturn(Completable.complete());

        new SaveDebitCardUseCaseImpl(debitCardRepository).execute(card).test().assertComplete();
        verify(debitCardRepository).save(card);
    }
}

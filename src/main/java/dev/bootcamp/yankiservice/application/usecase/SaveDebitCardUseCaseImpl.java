package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;

/**
 * Implementación de {@link SaveDebitCardUseCase}.
 */
@RequiredArgsConstructor
public class SaveDebitCardUseCaseImpl implements SaveDebitCardUseCase {
    private final DebitCardRepository debitCardRepository;

    @Override
    public Completable execute(DebitCard debitCard) {
        return debitCardRepository.save(debitCard);
    }
}

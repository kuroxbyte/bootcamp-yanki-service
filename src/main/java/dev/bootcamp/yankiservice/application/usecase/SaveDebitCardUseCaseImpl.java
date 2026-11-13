package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import io.reactivex.rxjava3.core.Completable;

/**
 * Implementación de {@link SaveDebitCardUseCase}.
 */
public class SaveDebitCardUseCaseImpl implements SaveDebitCardUseCase {
    private final DebitCardRepository debitCardRepository;

    /**
     * Crea el caso de uso con el adaptador de su puerto.
     */
    public SaveDebitCardUseCaseImpl(DebitCardRepository debitCardRepository) {
        this.debitCardRepository = debitCardRepository;
    }

    @Override
    public Completable execute(DebitCard debitCard) {
        return debitCardRepository.save(debitCard);
    }
}

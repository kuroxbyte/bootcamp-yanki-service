package dev.bootcamp.yankiservice.infrastructure.config;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.application.port.WalletEventPublisher;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.application.usecase.CreateWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.CreateWalletUseCaseImpl;
import dev.bootcamp.yankiservice.application.usecase.GetWalletUseCase;
import dev.bootcamp.yankiservice.application.usecase.GetWalletUseCaseImpl;
import dev.bootcamp.yankiservice.application.usecase.HandleOperationResultUseCase;
import dev.bootcamp.yankiservice.application.usecase.HandleOperationResultUseCaseImpl;
import dev.bootcamp.yankiservice.application.usecase.LinkDebitCardUseCase;
import dev.bootcamp.yankiservice.application.usecase.LinkDebitCardUseCaseImpl;
import dev.bootcamp.yankiservice.application.usecase.SaveDebitCardUseCase;
import dev.bootcamp.yankiservice.application.usecase.SaveDebitCardUseCaseImpl;
import dev.bootcamp.yankiservice.application.usecase.SendPaymentUseCase;
import dev.bootcamp.yankiservice.application.usecase.SendPaymentUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Registra los casos de uso como beans. Los casos de uso no dependen de Spring; aquí se les
 * inyectan los adaptadores de sus puertos.
 */
@Configuration
public class UseCaseConfig {

    /**
     * Reloj del sistema; con él se fechan los monederos, pagos y eventos.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Registra el caso de uso {@link CreateWalletUseCase}.
     */
    @Bean
    public CreateWalletUseCase createWalletUseCase(
            WalletRepository walletRepository,
            WalletEventPublisher walletEventPublisher,
            Clock clock) {
        return new CreateWalletUseCaseImpl(walletRepository, walletEventPublisher, clock);
    }

    /**
     * Registra el caso de uso {@link GetWalletUseCase}.
     */
    @Bean
    public GetWalletUseCase getWalletUseCase(
            WalletRepository walletRepository,
            YankiPaymentRepository paymentRepository,
            DebitCardLinkRepository linkRepository) {
        return new GetWalletUseCaseImpl(walletRepository, paymentRepository, linkRepository);
    }

    /**
     * Registra el caso de uso {@link SendPaymentUseCase}.
     */
    @Bean
    public SendPaymentUseCase sendPaymentUseCase(
            WalletRepository walletRepository,
            YankiPaymentRepository paymentRepository,
            DebitCardRepository debitCardRepository,
            AccountOperationPublisher operationPublisher) {
        return new SendPaymentUseCaseImpl(
                walletRepository,
                paymentRepository,
                debitCardRepository,
                operationPublisher);
    }

    /**
     * Registra el caso de uso {@link LinkDebitCardUseCase}.
     */
    @Bean
    public LinkDebitCardUseCase linkDebitCardUseCase(
            DebitCardRepository debitCardRepository,
            WalletRepository walletRepository,
            DebitCardLinkRepository linkRepository,
            AccountOperationPublisher operationPublisher,
            Clock clock) {
        return new LinkDebitCardUseCaseImpl(
                debitCardRepository,
                walletRepository,
                linkRepository,
                operationPublisher,
                clock);
    }

    /**
     * Registra el caso de uso {@link HandleOperationResultUseCase}.
     */
    @Bean
    public HandleOperationResultUseCase handleOperationResultUseCase(
            YankiPaymentRepository paymentRepository,
            DebitCardLinkRepository linkRepository,
            WalletRepository walletRepository,
            AccountOperationPublisher operationPublisher,
            Clock clock) {
        return new HandleOperationResultUseCaseImpl(
                paymentRepository,
                linkRepository,
                walletRepository,
                operationPublisher,
                clock);
    }

    /**
     * Registra el caso de uso {@link SaveDebitCardUseCase}.
     */
    @Bean
    public SaveDebitCardUseCase saveDebitCardUseCase(DebitCardRepository debitCardRepository) {
        return new SaveDebitCardUseCaseImpl(debitCardRepository);
    }
}

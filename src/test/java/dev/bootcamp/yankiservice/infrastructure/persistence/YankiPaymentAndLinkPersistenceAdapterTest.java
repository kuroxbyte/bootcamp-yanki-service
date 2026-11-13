package dev.bootcamp.yankiservice.infrastructure.persistence;

import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStatus;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStep;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
@Import({YankiPaymentPersistenceAdapter.class, DebitCardLinkPersistenceAdapter.class})
class YankiPaymentAndLinkPersistenceAdapterTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired private YankiPaymentPersistenceAdapter paymentAdapter;

    @Autowired private DebitCardLinkPersistenceAdapter linkAdapter;

    @Autowired private YankiPaymentMongoRepository paymentMongoRepository;

    @Autowired private DebitCardLinkMongoRepository linkMongoRepository;

    @BeforeEach
    void cleanCollections() {
        paymentMongoRepository.deleteAll().block();
        linkMongoRepository.deleteAll().block();
    }

    private YankiPayment payment(String source, String target, LocalDate date) {
        return new YankiPayment(
                source,
                "987654321",
                "a1",
                target,
                "912345678",
                null,
                new BigDecimal("12.50"),
                date);
    }

    @Test
    void savesPaymentKeepingTheSagaState() {
        YankiPayment payment = payment("w1", "w2", DATE);
        payment.setStep(PaymentStep.REFUNDING_SOURCE);
        payment.setRejectionReason("Cuenta bloqueada");

        YankiPayment saved = paymentAdapter.save(payment).blockingGet();
        YankiPayment found = paymentAdapter.findById(saved.getId()).blockingGet();

        assertThat(found.getAmount()).isEqualByComparingTo("12.50");
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.getStep()).isEqualTo(PaymentStep.REFUNDING_SOURCE);
        assertThat(found.getSourceAccountId()).isEqualTo("a1");
        assertThat(found.getTargetAccountId()).isNull();
        assertThat(found.getRejectionReason()).isEqualTo("Cuenta bloqueada");
    }

    @Test
    void listsPaymentsSentAndReceivedNewestFirst() {
        paymentAdapter.save(payment("w1", "w2", DATE.minusDays(1))).blockingGet();
        paymentAdapter.save(payment("w2", "w1", DATE)).blockingGet();
        paymentAdapter.save(payment("w2", "w3", DATE)).blockingGet();

        List<YankiPayment> payments = paymentAdapter.findByWalletId("w1").toList().blockingGet();

        assertThat(payments).extracting(YankiPayment::getDate)
                .containsExactly(DATE, DATE.minusDays(1));
    }

    @Test
    void savesAndFindsLinks() {
        DebitCardLink link = new DebitCardLink("c1", "d1", "w1", "987654321", DATE);
        link.setStatus(LinkStatus.TRANSFERRING);
        link.setTransferredBalance(new BigDecimal("80"));

        DebitCardLink saved = linkAdapter.save(link).blockingGet();

        DebitCardLink found = linkAdapter.findById(saved.getId()).blockingGet();
        assertThat(found.getStatus()).isEqualTo(LinkStatus.TRANSFERRING);
        assertThat(found.getTransferredBalance()).isEqualByComparingTo("80");
        assertThat(found.getCustomerId()).isEqualTo("c1");
        assertThat(linkAdapter.findByWalletId("w1").toList().blockingGet()).hasSize(1);
        assertThat(linkAdapter.findByWalletId("w2").toList().blockingGet()).isEmpty();
    }
}

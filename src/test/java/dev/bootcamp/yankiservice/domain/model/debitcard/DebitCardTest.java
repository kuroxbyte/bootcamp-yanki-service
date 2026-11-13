package dev.bootcamp.yankiservice.domain.model.debitcard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DebitCardTest {

    @Test
    void belongsOnlyToItsCustomer() {
        DebitCard card = new DebitCard("d1", "c1", "a1");

        assertThat(card.belongsTo("c1")).isTrue();
        assertThat(card.belongsTo("c2")).isFalse();
    }
}

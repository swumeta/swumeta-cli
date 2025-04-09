/*
 * Copyright (c) 2025 swumeta.net authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.swumeta.cli;

import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
class CardDatabaseServiceTests {
    @Autowired
    private CardDatabaseService svc;
    @Autowired
    private AppConfig config;

    @Test
    void testFindByNameSingleCard() {
        final var cards = svc.findByName("Hera Syndulla", "Spectre Two");
        assertThat(cards).hasSize(1);
        assertThat(cards.iterator().next().id()).isEqualTo("SOR-008");
    }

    @Test
    void testFindByNameOnly() {
        final var cards = svc.findByName("Hera Syndulla", null);
        assertThat(cards).hasSize(2);
        assertThat(cards.stream().map(Card::id)).containsExactlyInAnyOrder("SOR-008", "JTL-045");
    }

    @Test
    void testFindByNameUnknown() {
        assertThat(svc.findByName("Foo", "Bar")).isEmpty();
    }

    @Test
    void testFindByNameNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> svc.findByName(null, null));
    }

    @Test
    void testSave() throws IOException {
        final var cardFile = new File(new File(new File(config.database(), "cards"), "JTL"), "JTL-999.yaml");
        cardFile.delete();
        assertThat(cardFile.exists()).isFalse();

        final var card = new Card(Set.JTL, 999, Card.Type.EVENT, Card.Rarity.COMMON, Card.Arena.GROUND,
                List.of(Card.Aspect.AGGRESSION), 8, "Restart the game", "One more game?",
                URI.create("http://somewhere.com/myart"), URI.create("http://somewhere.com/thumnail"));
        svc.save(card);

        assertThat(cardFile.exists()).isTrue();
        final var cards = svc.findByName("Restart the game", "One more game?");
        assertThat(cards).hasSize(1);
        assertThat(cards.stream().map(Card::id)).containsExactly("JTL-999");
    }
}

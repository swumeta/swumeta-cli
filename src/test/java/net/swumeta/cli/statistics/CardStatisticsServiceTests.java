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

package net.swumeta.cli.statistics;

import net.swumeta.cli.TestHelper;
import net.swumeta.cli.model.Card;
import org.eclipse.collections.api.factory.Bags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CardStatisticsServiceTests {
    @Autowired
    private CardStatisticsService svc;
    @Autowired
    private TestHelper helper;

    @Test
    void testGetMostPlayedCards() {
        final var deck1 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 2),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 3)
        );
        final var deck2 = helper.createDeck(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 3),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 1)
        );
        final var stats = svc.getMostPlayedCards(List.of(deck1.source(), deck2.source()));
        assertThat(stats).isNotNull();
        assertThat(stats.sizeDistinct()).isEqualTo(6);
        assertThat(stats.occurrencesOf(Card.Id.valueOf("JTL-143"))).isEqualTo(5);
        assertThat(stats.occurrencesOf(Card.Id.valueOf("JTL-045"))).isEqualTo(4);
    }

    @Test
    void testGetMostPlayedLeadersStatistics() {
        final var deck1 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 2),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 3)
        );
        final var deck2 = helper.createDeck(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 3),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 1)
        );
        final var deck3 = helper.createDeck(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("JTL-026"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 2),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 3)
        );
        final var stats = svc.getMostPlayedCards(List.of(deck1.source(), deck2.source(), deck3.source()), c -> Card.Type.LEADER.equals(c.type()));
        assertThat(stats).isNotNull();
        assertThat(stats.sizeDistinct()).isEqualTo(2);
        assertThat(stats.occurrencesOf(Card.Id.valueOf("JTL-009"))).isEqualTo(1);
        assertThat(stats.occurrencesOf(Card.Id.valueOf("SOR-008"))).isEqualTo(2);
    }
}

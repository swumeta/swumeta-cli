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
import net.swumeta.cli.model.DeckArchetype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MostPlayedDecksStatisticsServiceTests {
    @Autowired
    private MostPlayedDecksStatisticsService svc;
    @Autowired
    private TestHelper helper;

    @Test
    void testGetMostPlayedDecksStatistics() {
        final var deck1 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck2 = helper.createDeck(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028"));
        final var deck3 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck4 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck5 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck6 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck7 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck8 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck9 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-028"));
        final var event = helper.createEvent("Foo", LocalDate.now(),
                List.of(deck1, deck2, deck3, deck4, deck5, deck6, deck7, deck8, deck9));

        final var top8Stats = svc.getMostPlayedDecksStatistics(List.of(event), 8);
        assertThat(top8Stats).isNotNull();
        assertThat(top8Stats.archetypes().sizeDistinct()).isEqualTo(2);
        assertThat(top8Stats.archetypes().occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("JTL-009"), Card.Aspect.AGGRESSION))).isEqualTo(7);
        assertThat(top8Stats.archetypes().occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-028")))).isEqualTo(0);
        assertThat(top8Stats.archetypes().occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028")))).isEqualTo(1);

        final var overallStats = svc.getMostPlayedDecksStatistics(List.of(event));
        assertThat(overallStats).isNotNull();
        assertThat(overallStats.archetypes().sizeDistinct()).isEqualTo(3);
        assertThat(overallStats.archetypes().occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("JTL-009"), Card.Aspect.AGGRESSION))).isEqualTo(7);
        assertThat(overallStats.archetypes().occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-028")))).isEqualTo(1);
        assertThat(overallStats.archetypes().occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028")))).isEqualTo(1);
    }
}

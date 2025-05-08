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
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.DeckArchetype;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DeckStatisticsServiceTests {
    @Autowired
    private DeckStatisticsService svc;
    @Autowired
    private TestHelper helper;

    @Test
    void testGetMostPlayedDecks() {
        final var deck1 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck2 = helper.createDeck(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028"));
        final var deck3 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck4 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck5 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck6 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck7 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck8 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck9 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-028"));

        final var overallStats = svc.getMostPlayedDecks(List.of(
                deck1.source(), deck2.source(), deck3.source(), deck4.source(), deck5.source(),
                deck6.source(), deck7.source(), deck8.source(), deck9.source()
        ));
        assertThat(overallStats).isNotNull();
        assertThat(overallStats.sizeDistinct()).isEqualTo(3);
        assertThat(overallStats.occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("JTL-009"), Card.Aspect.AGGRESSION))).isEqualTo(7);
        assertThat(overallStats.occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-028")))).isEqualTo(1);
        assertThat(overallStats.occurrencesOf(DeckArchetype.valueOf(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028")))).isEqualTo(1);
    }

    @Test
    void testLeaderMatchups() {
        final var deck1 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));
        final var deck2 = helper.createDeck(Card.Id.valueOf("SOR-008"), Card.Id.valueOf("SOR-028"));
        final var deck3 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"));

        helper.updateMatches(deck1, "1-1-0", List.of(
                new Deck.Match(1, "deck2", deck2.source(), Deck.Match.Result.WIN, "2-0-0"),
                new Deck.Match(2, "deck3", deck3.source(), Deck.Match.Result.LOSS, "1-2-0")
        ));
        helper.updateMatches(deck2, "0-2-0", List.of(
                new Deck.Match(1, "deck1", deck1.source(), Deck.Match.Result.LOSS, "0-2-0"),
                new Deck.Match(2, null, null, Deck.Match.Result.BYE, "1-0-0")
        ));
        helper.updateMatches(deck3, "2-0-0", List.of(
                new Deck.Match(1, null, null, Deck.Match.Result.BYE, "1-0-0"),
                new Deck.Match(2, "deck1", deck1.source(), Deck.Match.Result.WIN, "2-1-0")
        ));

        final var matchups = svc.getLeaderMatchups(List.of(deck1.source(), deck2.source(), deck3.source()));
        assertThat(matchups).hasSize(2);
        assertThat(matchups.get(0).leader()).isEqualTo(Card.Id.valueOf("JTL-009"));
        assertThat(matchups.get(0).metaShare()).isEqualTo(0.66, Offset.offset(0.1));
        assertThat(matchups.get(0).winRate()).isEqualTo(0.66d, Offset.offset(0.1));
        assertThat(matchups.get(1).leader()).isEqualTo(Card.Id.valueOf("SOR-008"));
        assertThat(matchups.get(1).metaShare()).isEqualTo(0.33, Offset.offset(0.1));
        assertThat(matchups.get(1).winRate()).isEqualTo(0d, Offset.offset(0.1));
    }
}

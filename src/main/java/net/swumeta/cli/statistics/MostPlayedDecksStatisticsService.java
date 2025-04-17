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

import net.swumeta.cli.DeckService;
import net.swumeta.cli.model.Event;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class MostPlayedDecksStatisticsService {
    private final Logger logger = LoggerFactory.getLogger(MostPlayedDecksStatisticsService.class);
    private DeckService deckService;

    MostPlayedDecksStatisticsService(DeckService deckService) {
        this.deckService = deckService;
    }

    public record MostPlayedDecksStatistics(
            ImmutableBag<DeckType> leaderBaseDecks,
            int rankingMax
    ) {
    }

    public MostPlayedDecksStatistics getMostPlayedDecksStatistics(Iterable<Event> events) {
        return getMostPlayedDecksStatistics(events, 0);
    }

    public MostPlayedDecksStatistics getMostPlayedDecksStatistics(Iterable<Event> events, int rankingMax) {
        logger.info("Computing statistics: most played decks ({})", rankingMax == 0 ? "all players" : ("top " + rankingMax));

        final var leaderBaseDecks = Bags.mutable.<DeckType>ofInitialCapacity(64);
        for (final var event : events) {
            logger.trace("Processing event: {}", event);
            for (final var e : event.decks()) {
                if (rankingMax != 0 && e.rank() > rankingMax) {
                    break;
                }
                logger.trace("Processing deck: {}", e.url());
                final var deck = deckService.load(e.url());
                final var deckType = new DeckType(deck.leader(), deck.base());
                leaderBaseDecks.add(deckType);
            }
        }
        return new MostPlayedDecksStatistics(leaderBaseDecks.toImmutableBag(), rankingMax);
    }
}

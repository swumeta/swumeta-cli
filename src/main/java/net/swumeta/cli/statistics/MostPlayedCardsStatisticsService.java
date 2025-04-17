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
public class MostPlayedCardsStatisticsService {
    private final Logger logger = LoggerFactory.getLogger(MostPlayedCardsStatisticsService.class);
    private final DeckService deckService;

    MostPlayedCardsStatisticsService(DeckService deckService) {
        this.deckService = deckService;
    }

    public record MostPlayedCardStatistics(ImmutableBag<String> cards) {
    }

    public MostPlayedCardStatistics getMostPlayedCardsStatistics(Iterable<Event> events) {
        logger.info("Computing statistics: most played cards");
        final var playedCards = Bags.mutable.<String>withInitialCapacity(512);
        for (final var event : events) {
            logger.trace("Processing event: {}", event);
            for (final var e : event.decks()) {
                final var deck = deckService.load(e.url());
                logger.trace("Processing deck: {}", deck.source());
                deck.main().forEachWithOccurrences((card, count) -> playedCards.addOccurrences(card, count));
                deck.sideboard().forEachWithOccurrences((card, count) -> playedCards.addOccurrences(card, count));
            }
        }
        return new MostPlayedCardStatistics(playedCards.toImmutableBag());
    }
}

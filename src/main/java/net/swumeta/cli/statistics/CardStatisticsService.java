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

import net.swumeta.cli.CardDatabaseService;
import net.swumeta.cli.DeckService;
import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Event;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
public class CardStatisticsService {
    private static final Predicate<Card> NULL_FILTER = e -> true;
    private final Logger logger = LoggerFactory.getLogger(CardStatisticsService.class);
    private final DeckService deckService;
    private final CardDatabaseService cardDatabaseService;

    CardStatisticsService(DeckService deckService, CardDatabaseService cardDatabaseService) {
        this.deckService = deckService;
        this.cardDatabaseService = cardDatabaseService;
    }

    public record CardStatistics(
            ImmutableBag<Card.Id> cards
    ) {
    }

    public CardStatistics getMostPlayedCards(Iterable<Event> events) {
        return getMostPlayedCards(events, null);
    }

    public CardStatistics getMostPlayedCards(Iterable<Event> events, @Nullable Predicate<Card> filter) {
        logger.debug("Computing statistics: most played cards");
        final var f = filter == null ? NULL_FILTER : filter;
        final var playedCards = Bags.mutable.<Card.Id>withInitialCapacity(64);
        for (final var event : events) {
            logger.trace("Processing event: {}", event);
            for (final var e : event.decks()) {
                if (e.url() == null) {
                    continue;
                }
                final var deck = deckService.load(e.url());
                logger.trace("Processing deck: {}", deck.source());
                if (!deck.isValid()) {
                    continue;
                }
                addCardOfType(deck.leader(), 1, f, playedCards);
                addCardOfType(deck.base(), 1, f, playedCards);
                deck.main().forEachWithOccurrences((card, count) -> addCardOfType(card, count, f, playedCards));
                deck.sideboard().forEachWithOccurrences((card, count) -> addCardOfType(card, count, f, playedCards));
            }
        }
        return new CardStatistics(playedCards.toImmutableBag());
    }

    private void addCardOfType(Card.Id cardId, int count, Predicate<Card> filter, MutableBag<Card.Id> output) {
        final var card = cardDatabaseService.findById(cardId);
        if (filter.test(card)) {
            output.addOccurrences(cardId, count);
        }
    }
}

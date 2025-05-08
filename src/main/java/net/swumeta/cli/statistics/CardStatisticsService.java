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
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
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

    public ImmutableBag<Card.Id> getMostPlayedCards(Iterable<URI> decks) {
        return getMostPlayedCards(decks, null);
    }

    public ImmutableBag<Card.Id> getMostPlayedCards(Iterable<URI> decks, Predicate<Card> cardFilter) {
        logger.debug("Computing statistics: most played cards");

        final var cards = Bags.mutable.<Card.Id>ofInitialCapacity(512);
        final var f = cardFilter != null ? cardFilter : new Predicate<Card>() {
            @Override
            public boolean test(Card card) {
                return true;
            }
        };

        for (final var deckUri : decks) {
            final var deck = deckService.load(deckUri);
            if (!deck.isValid()) {
                continue;
            }
            logger.trace("Processing deck: {}", deck.source());
            addCardsAfterFiltering(List.of(deck.leader(), deck.base()), f, cards);
            addCardsAfterFiltering(deck.main(), f, cards);
            addCardsAfterFiltering(deck.sideboard(), f, cards);
        }
        return cards.toImmutable();
    }

    private void addCardsAfterFiltering(Iterable<Card.Id> ids, Predicate<Card> filter, MutableBag<Card.Id> output) {
        for (final var id : ids) {
            final var card = cardDatabaseService.findById(id);
            if (filter.test(card)) {
                output.add(id);
            }
        }
    }
}

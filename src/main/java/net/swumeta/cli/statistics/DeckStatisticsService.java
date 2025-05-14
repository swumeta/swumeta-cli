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

import net.swumeta.cli.AppException;
import net.swumeta.cli.DeckService;
import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.DeckArchetype;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.tuple.primitive.ObjectIntPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Objects;
import java.util.stream.StreamSupport;

@Service
public class DeckStatisticsService {
    private static final ImmutableSet<Deck.Match.Result> VALID_MATCH_RESULTS = Sets.immutable.of(
            Deck.Match.Result.WIN, Deck.Match.Result.LOSS, Deck.Match.Result.DRAW
    );
    private final Logger logger = LoggerFactory.getLogger(DeckStatisticsService.class);
    private final DeckService deckService;

    DeckStatisticsService(DeckService deckService) {
        this.deckService = deckService;
    }

    public ImmutableBag<DeckArchetype> getMostPlayedDecks(Iterable<URI> decks) {
        logger.debug("Computing statistics: most played decks");
        return doGetMostPlayedDecks(decks);
    }

    private ImmutableBag<DeckArchetype> doGetMostPlayedDecks(Iterable<URI> decks) {
        return Bags.immutable.<DeckArchetype>fromStream(
                StreamSupport.stream(decks.spliterator(), false)
                        .map(uri -> {
                            try {
                                return deckService.load(uri);
                            } catch (AppException e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(Deck::isValid)
                        .map(deckService::getArchetype)
        );
    }

    public ImmutableList<LeaderMatchup> getLeaderMatchups(Iterable<URI> decks) {
        final var matchups = Maps.mutable.<LeaderOpponentKey, MutableBag<Deck.Match.Result>>ofInitialCapacity(32);
        final var leaders = Bags.mutable.<Card.Id>ofInitialCapacity(32);
        for (final var deckUri : decks) {
            final Deck deck;
            try {
                deck = deckService.load(deckUri);
            } catch (AppException e) {
                continue;
            }
            if (deck.leader() == null) {
                continue;
            }
            if ("--".equals(deck.matchRecord())) {
                logger.debug("Skipping deck from matchups: {}", deckUri);
                continue;
            }
            leaders.add(deck.leader());

            for (final var match : deck.matches()) {
                if (match.opponentDeck() == null) {
                    continue;
                }
                final Deck opDeck;
                try {
                    opDeck = deckService.load(match.opponentDeck());
                } catch (AppException e) {
                    continue;
                }
                if (opDeck.leader() == null) {
                    continue;
                }
                if ("--".equals(opDeck.matchRecord())) {
                    logger.debug("Skipping opponent deck from matchups: {}", opDeck.source());
                    continue;
                }
                final var key = new LeaderOpponentKey(deck.leader(), opDeck.leader());
                var allResults = matchups.get(key);
                if (allResults == null) {
                    allResults = Bags.mutable.<Deck.Match.Result>ofInitialCapacity(32);
                    matchups.put(key, allResults);
                }
                if (match.result() != null && VALID_MATCH_RESULTS.contains(match.result())) {
                    allResults.add(match.result());
                }
            }
        }

        final var sortedLeaders = leaders.topOccurrences(leaders.sizeDistinct()).collect(ObjectIntPair::getOne);
        final var allLeadersMatchups = Lists.mutable.<LeaderMatchup>ofInitialCapacity(leaders.size());
        for (final var leader : sortedLeaders) {
            final var leaderMatchups = Lists.mutable.<LeaderMatchupOpponent>ofInitialCapacity(leaders.size());
            final var resultsByOpponent = Maps.mutable.<Card.Id, MutableBag<Deck.Match.Result>>ofInitialCapacity(leaders.size());
            final var keys = matchups.keySet().stream().filter(k -> k.leader.equals(leader)).toList();
            for (final var key : keys) {
                var results = resultsByOpponent.get(key.opponent);
                if (results == null) {
                    results = Bags.mutable.<Deck.Match.Result>ofInitialCapacity(32);
                    resultsByOpponent.put(key.opponent, results);
                }
                results.addAllIterable(matchups.get(key));
            }
            for (final var op : sortedLeaders) {
                var resultForOp = resultsByOpponent.get(op);
                if (resultForOp == null) {
                    resultForOp = Bags.mutable.empty();
                }
                leaderMatchups.add(new LeaderMatchupOpponent(op, resultForOp.toImmutable()));
            }
            final double metaShare = leaders.occurrencesOf(leader) / (double) leaders.size();
            allLeadersMatchups.add(new LeaderMatchup(leader, metaShare, leaderMatchups.toImmutable()));
        }
        return allLeadersMatchups.toImmutableList();
    }

    public record LeaderMatchup(
            Card.Id leader,
            double metaShare,
            ImmutableList<LeaderMatchupOpponent> opponents
    ) {
        public double winRate() {
            final int matchCount = matchCount();
            return matchCount == 0 ? 0d : matchCount(Deck.Match.Result.WIN) / (double) matchCount;
        }

        public int matchCount() {
            int matchCount = 0;
            for (final var op : opponents) {
                matchCount += op.results.count(VALID_MATCH_RESULTS::contains);
            }
            return matchCount;
        }

        public int matchCount(Deck.Match.Result kind) {
            int matchCount = 0;
            for (final var op : opponents) {
                matchCount += op.results.occurrencesOf(kind);
            }
            return matchCount;
        }
    }

    public record LeaderMatchupOpponent(
            Card.Id opponent,
            ImmutableBag<Deck.Match.Result> results
    ) {
        public double winRate() {
            final int matchCount = matchCount();
            return matchCount == 0 ? 0d : matchCount(Deck.Match.Result.WIN) / (double) matchCount;
        }

        public int matchCount() {
            return results.count(VALID_MATCH_RESULTS::contains);
        }

        public int matchCount(Deck.Match.Result kind) {
            return results.occurrencesOf(kind);
        }
    }

    record LeaderOpponentKey(
            Card.Id leader,
            Card.Id opponent
    ) {
    }
}

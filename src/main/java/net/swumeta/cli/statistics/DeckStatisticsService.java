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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class DeckStatisticsService {
    private static final ImmutableSet<Deck.Match.Result> VALID_MATCH_RESULTS = Sets.immutable.of(
            Deck.Match.Result.WIN, Deck.Match.Result.LOSS, Deck.Match.Result.BYE, Deck.Match.Result.DRAW
    );
    private static final int META_MINIMUM_MATCH_COUNT = 8;
    private static final int META_MINIMUM_OPPONENT_COUNT = 3;
    private final Logger logger = LoggerFactory.getLogger(DeckStatisticsService.class);
    private final DeckService deckService;
    private final CardDatabaseService cardDatabaseService;

    DeckStatisticsService(DeckService deckService, CardDatabaseService cardDatabaseService) {
        this.deckService = deckService;
        this.cardDatabaseService = cardDatabaseService;
    }

    public ImmutableBag<DeckArchetype> getMostPlayedDecks(Iterable<URI> decks) {
        logger.debug("Computing statistics: most played decks");
        return doGetMostPlayedDecks(decks);
    }

    private ImmutableBag<DeckArchetype> doGetMostPlayedDecks(Iterable<URI> decks) {
        return Bags.immutable.<DeckArchetype>fromStream(
                StreamSupport.stream(decks.spliterator(), false)
                        .map(deckService::load)
                        .filter(Deck::isValid)
                        .map(deckService::getArchetype)
        );
    }

    public ImmutableList<LeaderMatchup> getLeaderMatchups(Iterable<URI> decks) {
        final var matchups = Maps.mutable.<LeaderOpponentKey, MutableBag<Deck.Match.Result>>ofInitialCapacity(32);
        final var leaders = Bags.mutable.<Card.Id>ofInitialCapacity(32);
        for (final var deckUri : decks) {
            final var deck = deckService.load(deckUri);
            if (deck.leader() == null) {
                continue;
            }
            leaders.add(deck.leader());

            for (final var match : deck.matches()) {
                if (match.opponentDeck() == null) {
                    continue;
                }
                final var opDeck = deckService.load(match.opponentDeck());
                if (opDeck.leader() == null) {
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

    public ImmutableList<DeckArchetypeMatchup> getMatchups(Iterable<URI> decks) {
        logger.debug("Computing statistics: matchups");

        final var mostPlayedDecks = doGetMostPlayedDecks(decks);
        final var matchups = Maps.mutable.<DeckArchetypeVersus, MutableBag<Deck.Match.Result>>ofInitialCapacity(mostPlayedDecks.sizeDistinct());
        for (final var deckUri : decks) {
            final var deck = deckService.load(deckUri);
            if (!deck.isValid()) {
                continue;
            }
            final var archetype = deckService.getArchetype(deck);
            for (final var m : deck.matches()) {
                if (m.opponentDeck() == null) {
                    continue;
                }
                final var opDeck = deckService.load(m.opponentDeck());
                if (!opDeck.isValid()) {
                    continue;
                }
                final var opArchetype = deckService.getArchetype(opDeck);
                if (archetype.equals(opArchetype)) {
                    continue;
                }
                final var key = new DeckArchetypeVersus(archetype, opArchetype);
                var results = matchups.get(key);
                if (results == null) {
                    results = Bags.mutable.ofInitialCapacity(32);
                    matchups.put(key, results);
                }
                results.add(m.result());
            }
        }

        for (final var i = matchups.values().iterator(); i.hasNext(); ) {
            final var results = i.next();
            if (results.size() < META_MINIMUM_MATCH_COUNT) {
                i.remove();
            }
        }

        final var output = Lists.mutable.<MutableDeckArchetypeMatchup>ofInitialCapacity(matchups.size());
        for (final var e : matchups.entrySet()) {
            MutableDeckArchetypeMatchup m = null;
            for (final var candidate : output) {
                if (candidate.archetype.equals(e.getKey().archetype)) {
                    m = candidate;
                    break;
                }
            }
            if (m == null) {
                final var archetype = e.getKey().archetype();
                final var metaShare = mostPlayedDecks.occurrencesOf(archetype) / (double) mostPlayedDecks.size();
                m = new MutableDeckArchetypeMatchup(archetype, metaShare, new ArrayList<>(8));
                output.add(m);
            }
            MutableDeckArchetypeOpponent op = null;
            for (final var candidate : m.opponents) {
                if (candidate.archetype.equals(e.getKey().opponent)) {
                    op = candidate;
                    break;
                }
            }
            if (op == null) {
                op = new MutableDeckArchetypeOpponent(e.getKey().opponent(), Bags.mutable.ofInitialCapacity(8));
                m.opponents.add(op);
            }
            op.results.addAll(e.getValue());
        }
        for (final var i = output.iterator(); i.hasNext(); ) {
            final var m = i.next();
            if (doubleEquals(m.metaShare, 0)) {
                i.remove();
            }
            if (m.opponents.isEmpty()) {
                i.remove();
            }
        }
        return Lists.immutable.fromStream(
                output.stream().map(MutableDeckArchetypeMatchup::toImmutable)
                        .sorted(Comparator.comparingDouble(DeckArchetypeMatchup::metaShare).reversed())
        );
    }

    record DeckArchetypeVersus(
            DeckArchetype archetype,
            DeckArchetype opponent
    ) {
    }

    private record MutableDeckArchetypeMatchup(
            DeckArchetype archetype,
            double metaShare,
            List<MutableDeckArchetypeOpponent> opponents
    ) {
        DeckArchetypeMatchup toImmutable() {
            return new DeckArchetypeMatchup(archetype, metaShare, Lists.immutable.fromStream(
                    opponents.stream().map(MutableDeckArchetypeOpponent::toImmutable).sorted(Comparator.reverseOrder()))
            );
        }
    }

    public record DeckArchetypeMatchup(
            DeckArchetype archetype,
            double metaShare,
            ImmutableList<DeckArchetypeOpponent> opponents
    ) implements Comparable<DeckArchetypeMatchup> {
        @Override
        public int compareTo(DeckArchetypeMatchup o) {
            if (archetype.equals(o.archetype)) {
                return 0;
            }
            final double winRate1 = winRate();
            final double winRate2 = o.winRate();
            if (doubleEquals(winRate1, winRate2)) {
                return 0;
            }
            return winRate1 < winRate2 ? -1 : 1;
        }

        public double winRate() {
            int winCount = 0;
            int total = 0;
            for (final var op : opponents) {
                winCount += op.results.occurrencesOf(Deck.Match.Result.WIN);
                total += op.results.size();
            }
            return winCount / (double) total;
        }

        public int matchCount() {
            int total = 0;
            for (final var op : opponents) {
                total += op.results.size();
            }
            return total;
        }
    }

    private static boolean doubleEquals(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    private record MutableDeckArchetypeOpponent(
            DeckArchetype archetype,
            MutableBag<Deck.Match.Result> results
    ) {
        DeckArchetypeOpponent toImmutable() {
            return new DeckArchetypeOpponent(archetype, results.toImmutable());
        }
    }

    public record DeckArchetypeOpponent(
            DeckArchetype archetype,
            ImmutableBag<Deck.Match.Result> results
    ) implements Comparable<DeckArchetypeOpponent> {
        @Override
        public int compareTo(DeckArchetypeOpponent o) {
            final var winRate1 = winRate();
            final var winRate2 = o.winRate();
            if (doubleEquals(winRate1, winRate2)) {
                return archetype.compareTo(o.archetype);
            }
            return winRate1 < winRate2 ? -1 : 1;
        }

        public double winRate() {
            int winCount = results.occurrencesOf(Deck.Match.Result.WIN);
            int total = results.size();
            return winCount / (double) total;
        }
    }

    public record LeaderMatchup(
            Card.Id leader,
            double metaShare,
            ImmutableList<LeaderMatchupOpponent> opponents
    ) {
        public double winRate() {
            int matchCount = 0;
            int winCount = 0;
            for (final var op : opponents) {
                matchCount += op.results.size();
                winCount += op.results.occurrencesOf(Deck.Match.Result.WIN);
            }
            return matchCount == 0 ? 0d : winCount / (double) matchCount;
        }

        public int matchCount() {
            int matchCount = 0;
            for (final var op : opponents) {
                matchCount += op.results.size();
            }
            return matchCount;
        }
    }

    public record LeaderMatchupOpponent(
            Card.Id opponent,
            ImmutableBag<Deck.Match.Result> results
    ) {
        public double winRate() {
            return results.isEmpty() ? 0d : results.occurrencesOf(Deck.Match.Result.WIN) / (double) results.size();
        }
    }

    record MutableLeaderMatchupEntry(
            Card.Id opponent,
            MutableBag<Deck.Match.Result> results
    ) {
    }

    record LeaderOpponentKey(
            Card.Id leader,
            Card.Id opponent
    ) {
    }
}

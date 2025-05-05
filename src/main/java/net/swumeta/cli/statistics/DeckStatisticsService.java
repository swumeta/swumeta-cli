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
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.DeckArchetype;
import net.swumeta.cli.model.Event;
import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class DeckStatisticsService {
    private static final int META_MINIMUM_MATCH_COUNT = 8;
    private static final int META_MAXIMUM_PLAYER_RANK = 64;
    private final Logger logger = LoggerFactory.getLogger(DeckStatisticsService.class);
    private DeckService deckService;

    DeckStatisticsService(DeckService deckService) {
        this.deckService = deckService;
    }

    public ImmutableBag<DeckArchetype> getMostPlayedDecks(Iterable<Event> events) {
        return getMostPlayedDecks(events, 0);
    }

    public ImmutableBag<DeckArchetype> getMostPlayedDecks(Iterable<Event> events, int rankingMax) {
        logger.debug("Computing statistics: most played decks ({})", rankingMax == 0 ? "all players" : ("top " + rankingMax));

        final var archetypes = Bags.mutable.<DeckArchetype>ofInitialCapacity(64);
        for (final var event : events) {
            logger.trace("Processing event: {}", event);
            for (final var e : event.decks()) {
                if (rankingMax != 0 && e.rank() > rankingMax) {
                    break;
                }
                if (e.url() == null) {
                    continue;
                }
                logger.trace("Processing deck: {}", e.url());
                final var deck = deckService.load(e.url());
                if (!deck.isValid()) {
                    continue;
                }
                final var deckType = deckService.getArchetype(deck);
                archetypes.add(deckType);
            }
        }
        return archetypes.toImmutableBag();
    }

    public ImmutableList<DeckArchetypeMatchup> getMatchups(Iterable<Event> events) {
        logger.debug("Computing matchups");

        final var events64players = Lists.immutable.fromStream(
                Lists.mutable.withAll(events).stream().filter(e -> e.players() >= META_MAXIMUM_PLAYER_RANK)
        );
        final var mostPlayedDecks = getMostPlayedDecks(events64players, 64);
        final var matchups = Maps.mutable.<DeckArchetypeVersus, MutableBag<Deck.Match.Result>>ofInitialCapacity(mostPlayedDecks.sizeDistinct());

        for (final var event : events64players) {
            for (final var deck : getEventDecks(event)) {
                final var archetype = deckService.getArchetype(deck);
                if (!mostPlayedDecks.contains(archetype)) {
                    continue;
                }
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

    private LazyIterable<Deck> getEventDecks(Event event) {
        return Lists.immutable.fromStream(
                event.decks().stream()
                        .filter(d -> d.rank() <= META_MAXIMUM_PLAYER_RANK)
                        .map(Event.DeckEntry::url)
                        .filter(Objects::nonNull)
                        .map(deckService::load)
                        .filter(Deck::isValid)
        ).asLazy();
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
}

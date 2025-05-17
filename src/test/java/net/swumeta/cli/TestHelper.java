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

package net.swumeta.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.swumeta.cli.model.*;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class TestHelper {
    private final Logger logger = LoggerFactory.getLogger(TestHelper.class);
    private final ObjectMapper objectMapper;

    TestHelper() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    public Event createEvent(String name, LocalDate date, List<Deck> decks) {
        final var deckEntries = new ArrayList<Event.DeckEntry>(decks.size());
        for (int i = 1; i <= decks.size(); ++i) {
            deckEntries.add(new Event.DeckEntry(i, false, decks.get(i - 1).source(), null, null, null));
        }
        return new Event(name, false, Event.Type.PQ, 64, date, new Location("France", "Paris"), false, Format.PREMIER,
                URI.create("http://melee.gg/foo"), List.of("Me"), List.of(), deckEntries);
    }

    public Deck createDeck(Card.Id leader, Card.Id base) {
        return createDeck(leader, base, Bags.immutable.empty(), Bags.immutable.empty());
    }

    public Deck createDeck(Card.Id leader, Card.Id base, ImmutableBag<Card.Id> main, ImmutableBag<Card.Id> sideboard) {
        return createDeck(leader, base, main, sideboard, "0-0-0", List.of());
    }

    public Deck updateMatches(Deck deck, String matchRecord, List<Deck.Match> matches) {
        try {
            final var deckFile = new File(UriComponentsBuilder.fromUri(deck.source()).scheme("file").build().toUri());
            logger.trace("Updating test deck to file: {}", deckFile);
            final var newDeck = new Deck(
                    deck.source(), deck.player(), deck.format(), deck.leader(), deck.base(), deck.main(), deck.sideboard(),
                    matchRecord, matches
            );
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(deckFile, newDeck);
            return newDeck;
        } catch (IOException e) {
            throw new AppException("Failed to update test deck", e);
        }
    }

    private Deck createDeck(Card.Id leader, Card.Id base, ImmutableBag<Card.Id> main, ImmutableBag<Card.Id> sideboard, String matchRecord,
                            List<Deck.Match> matches) {
        try {
            final var deckFile = File.createTempFile("deck-", ".yaml");
            deckFile.deleteOnExit();

            final var testUri = UriComponentsBuilder.fromUri(deckFile.toURI()).scheme("testfile").build().toUri();
            final var deck = new Deck(testUri, "Me", Format.PREMIER, leader, base, main, sideboard, matchRecord, matches);
            logger.trace("Saving test deck to file: {}", deckFile);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(deckFile, deck);
            return deck;
        } catch (IOException e) {
            throw new AppException("Failed to create test deck", e);
        }
    }
}

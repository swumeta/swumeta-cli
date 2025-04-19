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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.DeckArchetype;
import net.swumeta.cli.model.Format;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.factory.Bags;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class DeckService {
    private static final Map<Card.Aspect, Card.Id> DEFAULT_BASES = Map.of(
            Card.Aspect.VIGILANCE, Card.Id.valueOf("SOR-019"),
            Card.Aspect.COMMAND, Card.Id.valueOf("SOR-023"),
            Card.Aspect.AGGRESSION, Card.Id.valueOf("SOR-026"),
            Card.Aspect.CUNNING, Card.Id.valueOf("SOR-029")
    );
    private final Logger logger = LoggerFactory.getLogger(DeckService.class);
    private final CardDatabaseService cardDatabaseService;
    private final RestClient client;
    private final AppConfig config;
    private final ObjectMapper yamlObjectMapper;
    private final ObjectMapper jsonObjectMapper;
    private final LoadingCache<URI, Deck> deckCache = Caffeine.newBuilder().weakKeys().weakValues().build(this::doLoad);

    DeckService(CardDatabaseService cardDatabaseService, RestClient client, AppConfig config) {
        this.cardDatabaseService = cardDatabaseService;
        this.client = client;
        this.config = config;
        this.jsonObjectMapper = new ObjectMapper();
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    }

    public Deck load(URI uri) {
        return deckCache.get(uri);
    }

    private File toCachedFile(URI uri) {
        final var deckCacheDir = new File(config.cache(), "decks");
        final var deckFileName = md5(UriComponentsBuilder.fromUri(uri).port(80).toUriString()) + ".yaml";
        final var dirLevel1 = deckFileName.substring(0, 1);
        final var dirLevel2 = deckFileName.substring(0, 2);
        return new File(new File(new File(deckCacheDir, dirLevel1), dirLevel2), deckFileName);
    }

    private Deck doLoad(URI uri) {
        final var deckFile = toCachedFile(uri);
        if (deckFile.exists()) {
            try {
                logger.debug("Loading deck from cache: {}", uri);
                return yamlObjectMapper.readerFor(Deck.class).readValue(deckFile);
            } catch (IOException e) {
                logger.debug("Unable to read cached deck file: {}", deckFile, e);
            }
        }

        final var host = uri.getHost();
        final var deck = loadMeleeDeck(uri);

        logger.debug("Caching deck: {}", uri);
        if (!deckFile.getParentFile().exists()) {
            deckFile.getParentFile().mkdirs();
        }
        try {
            yamlObjectMapper.writerFor(Deck.class).writeValue(deckFile, deck);
        } catch (IOException e) {
            logger.warn("Failed to cache deck: {}", uri, e);
        }
        return deck;
    }

    public ImmutableBag<Card> getCards(Deck deck) {
        final var cardIds = deck.main().newWithAll(deck.sideboard());
        if (cardIds.isEmpty()) {
            return Bags.immutable.empty();
        }
        final var cards = Bags.mutable.<Card>withInitialCapacity(cardIds.size());
        cardIds.forEachWithOccurrences((cardId, count) -> {
            final var card = cardDatabaseService.findById(cardId);
            cards.addOccurrences(card, count);
        });
        return cards.toImmutableBag();
    }

    public DeckArchetype getArchetype(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        final var baseCard = cardDatabaseService.findById(deck.base());
        return !baseCard.rarity().equals(Card.Rarity.COMMON) || baseCard.aspects().isEmpty()
                ? DeckArchetype.valueOf(deck.leader(), deck.base())
                : DeckArchetype.valueOf(deck.leader(), baseCard.aspects().get(0));
    }

    public String formatArchetype(DeckArchetype archetype) {
        Assert.notNull(archetype, "Deck archetype must not be null");
        final var leaderCard = cardDatabaseService.findById(archetype.leader());
        final var base = archetype.base() == null ? DEFAULT_BASES.get(archetype.aspect()) : archetype.base();
        return "%s (%s) - %s".formatted(
                leaderCard.name(),
                leaderCard.set(),
                formatBase(base)
        );
    }

    public String formatName(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        return formatArchetype(getArchetype(deck));
    }

    public String formatLeader(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        final var leader = cardDatabaseService.findById(deck.leader());
        return new StringBuffer(32).append(leader.name()).append(" (").append(leader.set()).append(")").toString();
    }

    public String formatBase(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        return formatBase(deck.base());
    }

    private String formatBase(Card.Id base) {
        Assert.notNull(base, "Base must not be null");
        final var baseCard = cardDatabaseService.findById(base);
        if (baseCard.rarity().equals(Card.Rarity.COMMON)) {
            if (baseCard.aspects().isEmpty()) {
                return baseCard.name();
            }
            return switch (baseCard.aspects().get(0)) {
                case VIGILANCE -> "Blue";
                case COMMAND -> "Green";
                case AGGRESSION -> "Red";
                case CUNNING -> "Yellow";
                default -> baseCard.name();
            };
        }
        return baseCard.name();
    }

    public String toSwudbJson(Deck deck) {
        final var swudbDeck = new ArrayList<JsonSwudbCard>(50);
        if (deck.main() != null) {
            deck.main().forEachWithOccurrences((ObjectIntProcedure<Card.Id>) (card, count) -> swudbDeck.add(new JsonSwudbCard(card.toString().replace("-", "_"), count)));
        }
        final var swudbSideboard = new ArrayList<JsonSwudbCard>(10);
        if (deck.sideboard() != null) {
            deck.sideboard().forEachWithOccurrences((ObjectIntProcedure<Card.Id>) (card, count) -> swudbSideboard.add(new JsonSwudbCard(card.toString().replace("-", "_"), count)));
        }
        final var d = new JsonSwudbDeck(
                new JsonSwudbMetadata(formatName(deck), deck.author()),
                new JsonSwudbCard(deck.leader().toString().replace("-", "_"), 1),
                new JsonSwudbCard(deck.base().toString().replace("-", "_"), 1),
                swudbDeck,
                swudbSideboard
        );
        try {
            return yamlObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new AppException("Failed to convert deck to swudb format", e);
        }
    }

    private record JsonSwudbDeck(
            JsonSwudbMetadata metadata,
            JsonSwudbCard leader,
            JsonSwudbCard base,
            List<JsonSwudbCard> deck,
            List<JsonSwudbCard> sideboard
    ) {
    }

    private record JsonSwudbMetadata(
            String name,
            String author
    ) {
    }

    private record JsonSwudbCard(
            String id,
            int count
    ) {
    }

    private Deck loadMeleeDeck(URI uri) {
        if ("testfile".equals(uri.getScheme())) {
            final var testUri = UriComponentsBuilder.fromUri(uri).scheme("file").build().toUri();
            logger.debug("Loading test deck from file: {}", testUri);
            try (final var in = testUri.toURL().openStream()) {
                return yamlObjectMapper.readerFor(Deck.class).readValue(in);
            } catch (IOException e) {
                throw new AppException("Failed to read test deck from file: " + uri, e);
            }
        }
        logger.debug("Loading deck from melee.gg: {}", uri);
        final var meleePage = client.get().uri(uri).retrieve().body(String.class);
        final var meleeDoc = Jsoup.parse(meleePage);

        String author = "melee.gg";
        for (final var aElem : meleeDoc.getElementsByTag("a")) {
            if (aElem.hasAttr("href")) {
                final var href = aElem.attr("href");
                if (href.startsWith("/Profile/Index/")) {
                    author = href.replace("/Profile/Index/", "");
                    break;
                }
            }
        }

        final var swuContent = meleeDoc.getElementById("decklist-swu-text").text();
        final var lines = swuContent.split("\\r\\n|\\n|\\r");

        Card.Id leader = null;
        Card.Id base = null;
        final var main = Bags.mutable.<Card.Id>ofInitialCapacity(50);
        final var sideboard = Bags.mutable.<Card.Id>ofInitialCapacity(10);

        boolean inSectionLeaders = false;
        boolean inSectionBase = false;
        boolean inSectionDeck = false;
        boolean inSectionSideboard = false;

        for (final var line : lines) {
            if (line.equals("Leaders")) {
                inSectionLeaders = true;
                inSectionBase = false;
                inSectionDeck = false;
                inSectionSideboard = false;
            } else if (line.equals("Base")) {
                inSectionLeaders = false;
                inSectionBase = true;
                inSectionDeck = false;
                inSectionSideboard = false;
            } else if (line.equals("Deck")) {
                inSectionLeaders = false;
                inSectionBase = false;
                inSectionDeck = true;
                inSectionSideboard = false;
            } else if (line.equals("Sideboard")) {
                inSectionLeaders = false;
                inSectionBase = false;
                inSectionDeck = false;
                inSectionSideboard = true;
            } else if (line.contains("|")) {
                final var parts = line.split("\\s*\\|\\s*");
                if (parts.length != 2 && parts.length != 3) {
                    throw new RuntimeException("Invalid line: " + line);
                }
                final var quantity = Integer.parseInt(parts[0]);
                final var cardTitle = parts[1];
                final var cardSubtitle = parts.length > 2 ? parts[2] : null;
                final var cards = cardDatabaseService.findByName(cardTitle, cardSubtitle);
                if (cards.isEmpty()) {
                    logger.warn("Unable to find card: {}", line);
                } else {
                    final var card = cards.iterator().next();
                    if (inSectionLeaders) {
                        leader = card.id();
                    } else if (inSectionBase) {
                        base = card.id();
                    } else if (inSectionDeck) {
                        main.addOccurrences(card.id(), quantity);
                    } else if (inSectionSideboard) {
                        sideboard.addOccurrences(card.id(), quantity);
                    }
                }
            }
        }
        return new Deck(
                uri,
                author,
                Format.PREMIER,
                leader,
                base,
                main.toImmutableBag(),
                sideboard.toImmutableBag()
        );
    }

    private record SwudbDeck(
            String authorName,
            SwudbCard leader,
            SwudbCard base,
            List<SwudbContent> shuffledDeck
    ) {
    }

    private record SwudbCard(
            String defaultExpansionAbbreviation,
            String defaultCardNumber
    ) {
    }

    private record SwudbContent(
            int count,
            int sideboardCount,
            SwudbCard card
    ) {
    }

    private static String md5(String name) {
        try {
            final var md = MessageDigest.getInstance("MD5");
            final var digest = md.digest(name.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to calculate MD5 sum", e);
        }
    }
}

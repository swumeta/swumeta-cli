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
import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.Format;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

@Service
public class DeckService {
    private final Logger logger = LoggerFactory.getLogger(DeckService.class);
    private final CardDatabaseService cardDatabaseService;
    private final RestClient client;
    private final AppConfig config;
    private final ObjectMapper objectMapper;

    DeckService(CardDatabaseService cardDatabaseService, RestClient client, AppConfig config) {
        this.cardDatabaseService = cardDatabaseService;
        this.client = client;
        this.config = config;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    public Deck load(URI uri) {
        return load(uri, false);
    }

    public Deck load(URI uri, boolean skipCache) {
        final var deckCacheDir = new File(config.cache(), "decks");
        final var deckFile = new File(deckCacheDir, md5(uri.toASCIIString()) + ".yaml");
        if (!skipCache && deckFile.exists()) {
            try {
                logger.debug("Loading deck from cache: {}", uri);
                return objectMapper.readerFor(Deck.class).readValue(deckFile);
            } catch (IOException e) {
                logger.debug("Unable to read cached deck file: {}", deckFile, e);
            }
        }

        final var host = uri.getHost();
        Deck deck = null;
        if (host.endsWith("swudb.com")) {
            deck = loadSwudbDeck(uri);
        } else if (host.endsWith("melee.gg")) {
            deck = loadMeleeDeck(uri);
        }
        if (deck == null) {
            throw new RuntimeException("Unsupported deck URI: " + uri);
        }

        logger.debug("Saving deck to cache: {}", uri);
        if (!deckCacheDir.exists()) {
            deckCacheDir.mkdirs();
        }
        try {
            objectMapper.writerFor(Deck.class).writeValue(deckFile, deck);
        } catch (IOException e) {
            logger.warn("Failed to cache deck: {}", uri, e);
        }
        return deck;
    }

    private Deck loadSwudbDeck(URI uri) {
        logger.debug("Loading deck from swudb.com: {}", uri);
        final String jsonDeckUri;
        if (uri.getPath().startsWith("/api")) {
            jsonDeckUri = uri.toASCIIString();
        } else {
            jsonDeckUri = UriComponentsBuilder.fromUri(uri).replacePath("/api/" + uri.getPath()).toUriString();
        }
        logger.debug("Loading deck content: {}", jsonDeckUri);
        final var swudbDeck = client.get().uri(jsonDeckUri).retrieve().body(SwudbDeck.class);
        logger.trace("Loaded deck from swudb.com: {}", swudbDeck);

        final var main = new ArrayList<Card>(50);
        final var sideboard = new ArrayList<Card>(10);
        for (final SwudbContent c : swudbDeck.shuffledDeck) {
            if (c.count != 0) {
                final var card = toCard(c.card);
                for (int i = 0; i < c.count; ++i) {
                    main.add(card);
                }
            }
            if (c.sideboardCount != 0) {
                final var card = toCard(c.card);
                for (int i = 0; i < c.sideboardCount; ++i) {
                    sideboard.add(card);
                }
            }
        }
        return new Deck(
                uri,
                swudbDeck.authorName(),
                Format.PREMIER,
                toCard(swudbDeck.leader),
                toCard(swudbDeck.base),
                main,
                sideboard
        );
    }

    private Card toCard(SwudbCard c) {
        final var cards = cardDatabaseService.findByName(c.cardName, c.title);
        if (cards.isEmpty()) {
            throw new RuntimeException("No card found: " + c.cardName + " | " + c.title);
        }
        return cards.iterator().next();
    }

    private Deck loadMeleeDeck(URI uri) {
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

        Card leader = null;
        Card base = null;
        final var main = new ArrayList<Card>(50);
        final var sideboard = new ArrayList<Card>(10);

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
                    throw new RuntimeException("Unable to find card: " + line);
                }

                final var card = cards.iterator().next();
                if (inSectionLeaders) {
                    leader = card;
                } else if (inSectionBase) {
                    base = card;
                } else if (inSectionDeck) {
                    for (int i = 0; i < quantity; ++i) {
                        main.add(card);
                    }
                } else if (inSectionSideboard) {
                    for (int i = 0; i < quantity; ++i) {
                        sideboard.add(card);
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
                main,
                sideboard
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
            String cardName,
            String title
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

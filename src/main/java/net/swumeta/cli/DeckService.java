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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DeckService {
    private static final int CURRENT_VERSION = 2;
    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+)-(\\d+)-(\\d+)");
    private static final Pattern SCORE_PATTERN2 = Pattern.compile("(\\d+)-(\\d+)");
    private static final Map<Card.Id, String> CARD_NAME_ALIASES = Map.of(
            Card.Id.valueOf("SOR-022"), "ECL"
    );
    private final Logger logger = LoggerFactory.getLogger(DeckService.class);
    private final CardDatabaseService cardDatabaseService;
    private final RestClient client;
    private final AppConfig config;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlObjectMapper;
    private final LoadingCache<URI, Deck> deckCache = Caffeine.newBuilder().weakKeys().weakValues().build(this::doLoad);
    private final LoadingCache<URI, DeckArchetype> deckArchetypeCache = Caffeine.newBuilder().weakKeys().weakValues().build(this::createArchetype);

    DeckService(CardDatabaseService cardDatabaseService, RestClient client, AppConfig config) {
        this.cardDatabaseService = cardDatabaseService;
        this.client = client;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    }

    public Deck load(URI uri) {
        return deckCache.get(uri);
    }

    public void delete(Iterable<URI> decks) {
        if (decks == null) {
            return;
        }
        for (final var deckUri : decks) {
            if (deckUri == null) {
                continue;
            }
            final var deckFile = toCachedFile(deckUri);
            if (deckFile.exists()) {
                deckFile.delete();
            }
            final var skipDeckFile = new File(deckFile + ".skip");
            if (skipDeckFile.exists()) {
                skipDeckFile.delete();
            }
        }
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
        if (!deckFile.getParentFile().exists()) {
            deckFile.getParentFile().mkdirs();
        }
        final var skipMarkerFile = new File(deckFile + ".skip");
        if (skipMarkerFile.exists()) {
            throw new AppException("Skipping melee.gg deck: " + uri);
        }

        Deck deck = null;
        if (deckFile.exists()) {
            try {
                final var versionedDeck = yamlObjectMapper.readValue(deckFile, VersionedDeck.class);
                if (CURRENT_VERSION == versionedDeck.version) {
                    logger.debug("Loading deck from cache: {}", uri);
                    deck = yamlObjectMapper.readerFor(Deck.class).readValue(deckFile);
                }
            } catch (IOException e) {
                logger.debug("Unable to read cached deck file: {}", deckFile, e);
            }
        }
        if (deck != null) {
            return deck;
        }

        if ("testfile".equals(uri.getScheme())) {
            final var testUri = UriComponentsBuilder.fromUri(uri).scheme("file").build().toUri();
            logger.debug("Loading test deck from file: {}", testUri);
            try (final var in = testUri.toURL().openStream()) {
                return yamlObjectMapper.readerFor(Deck.class).readValue(in);
            } catch (IOException e) {
                throw new AppException("Failed to read test deck from file: " + uri, e);
            }
        }

        try {
            final var host = uri.getHost();
            if (host != null) {
                if (host.contains("melee")) {
                    deck = loadMeleeDeck(uri);
                } else if (host.contains("swudb")) {
                    deck = loadSwudbDeck(uri);
                }
            }
        } catch (AppException e) {
            logger.debug("Unable to load deck: {}", uri, e);
        }
        if (deck == null) {
            logger.warn("Failed to load deck from melee.gg: {}", uri);
            deck = null;
            try {
                Files.writeString(skipMarkerFile.toPath(), uri.toASCIIString(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            } catch (IOException ignore) {
            }
            throw new AppException("Failed to load deck: " + uri);
        }

        logger.debug("Caching deck: {}", uri);
        try {
            final var buf = new ByteArrayOutputStream(1024);
            yamlObjectMapper.writeValue(buf, deck);
            new PrintWriter(buf, true).println("version: %s".formatted(CURRENT_VERSION));

            try (final var out = new FileOutputStream(deckFile)) {
                StreamUtils.copy(buf.toByteArray(), out);
            }
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
        return deckArchetypeCache.get(deck.source());
    }

    private DeckArchetype createArchetype(URI uri) {
        final var deck = load(uri);
        final var baseCard = cardDatabaseService.findById(deck.base());
        return !baseCard.rarity().equals(Card.Rarity.COMMON) || baseCard.aspects().isEmpty()
                ? DeckArchetype.valueOf(deck.leader(), deck.base())
                : DeckArchetype.valueOf(deck.leader(), baseCard.aspects().get(0));
    }

    public String formatArchetype(DeckArchetype archetype) {
        Assert.notNull(archetype, "Deck archetype must not be null");
        final var leaderCard = cardDatabaseService.findById(archetype.leader());
        final var base = lookupBase(archetype);
        return "%s (%s) - %s".formatted(
                leaderCard.name(),
                leaderCard.set(),
                formatBase(base)
        );
    }

    public Card.Id lookupBase(DeckArchetype archetype) {
        return archetype.base() == null ? archetype.aspect().toGenericBase() : archetype.base();
    }

    public String formatName(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        return formatArchetype(getArchetype(deck));
    }

    public String formatLeader(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        return formatLeader(deck.leader());
    }

    public String formatLeader(Card.Id leader) {
        Assert.notNull(leader, "Leader must not be null");
        final var leaderCard = cardDatabaseService.findById(leader);
        return new StringBuffer(32).append(leaderCard.name()).append(" (").append(leader.set()).append(")").toString();
    }

    public String formatBase(Deck deck) {
        Assert.notNull(deck, "Deck must not be null");
        return formatBase(deck.base());
    }

    public String formatBase(Card.Id base) {
        Assert.notNull(base, "Base must not be null");
        final var alias = CARD_NAME_ALIASES.get(base);
        if (alias != null) {
            return alias;
        }
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
                new JsonSwudbMetadata(formatName(deck), deck.player()),
                new JsonSwudbCard(deck.leader().toString().replace("-", "_"), 1),
                new JsonSwudbCard(deck.base().toString().replace("-", "_"), 1),
                swudbDeck,
                swudbSideboard
        );
        Collections.sort(d.deck);
        Collections.sort(d.sideboard);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(d);
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
    ) implements Comparable<JsonSwudbCard> {
        @Override
        public int compareTo(JsonSwudbCard o) {
            if (id.compareTo(o.id) != 0) {
                return id.compareTo(o.id);
            }
            if (count == o.count) {
                return 0;
            }
            return count < o.count ? -1 : 1;
        }
    }

    private Deck loadMeleeDeck(URI uri) {
        logger.info("Loading deck from melee.gg: {}", uri);
        final var meleePage = client.get().uri(uri).retrieve().body(String.class);
        final var meleeDoc = Jsoup.parse(meleePage);

        final var swuContentElem = meleeDoc.getElementById("decklist-swu-text");
        if (swuContentElem == null) {
            throw new AppException("No swudb.com content found: " + uri);
        }
        final var swuContent = swuContentElem.text().trim();
        if (swuContent.isEmpty()) {
            throw new AppException("No swudb.com content found: " + uri);
        }
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
                final var parts = line.split("\\s*[|]\\s*");
                if (parts.length != 2 && parts.length != 3) {
                    throw new RuntimeException("Invalid line: " + line);
                }
                final var quantity = Integer.parseInt(parts[0]);
                final var cardTitle = parts[1];
                final var cardSubtitle = parts.length > 2 ? parts[2] : null;
                final var cards = cardDatabaseService.findByName(cardTitle, cardSubtitle);
                if (cards.isEmpty()) {
                    logger.debug("Unable to find card: {}", line);
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

        if (leader == null || base == null) {
            throw new AppException("No leader or base set in deck: " + uri);
        }

        final var meleeDeckId = UriComponentsBuilder.fromUri(uri).build().getPathSegments().getLast();
        final var meleeDeckDetailsUri = UriComponentsBuilder.fromUri(uri).replacePath("Decklist").pathSegment("GetTournamentViewData", meleeDeckId).build().toUri();
        logger.debug("Melee.gg deck details URI: {} -> {}", uri, meleeDeckDetailsUri);
        final var meleeDeckWrapper = client.get().uri(meleeDeckDetailsUri).accept(MediaType.APPLICATION_JSON).retrieve().body(MeleeDeckWrapper.class);
        final MeleeDeckDetails meleeDeckDetails;
        try {
            meleeDeckDetails = objectMapper.readerFor(MeleeDeckDetails.class).readValue(meleeDeckWrapper.json);
        } catch (JsonProcessingException e) {
            throw new AppException("Failed to parse Melee.gg deck details: " + uri, e);
        }
        logger.trace("Melee.gg deck details: {}", meleeDeckDetails);

        final var player = meleeDeckDetails.team.user;
        final var matchRecord = meleeDeckDetails.team.matchRecord;

        final var tournamentId = Integer.parseInt(meleeDoc.getElementById("tournament-id-field").val());
        final var matches = meleeDeckDetails.matches.stream().map(m -> toDeckMatch(tournamentId, m)).toList();

        return new Deck(
                uri,
                player,
                Format.PREMIER,
                leader,
                base,
                main.toImmutableBag(),
                sideboard.toImmutableBag(),
                matchRecord,
                matches
        );
    }

    private Deck loadSwudbDeck(URI uri) {
        logger.info("Loading deck from swudb.com: {}", uri);
        final var deckId = UriComponentsBuilder.fromUri(uri).build().getPathSegments().getLast();
        final var deckUri = UriComponentsBuilder.fromUri(uri).replacePath("/api/").pathSegment("deck", deckId).build().toUri();
        final var swudbDeck = client.get().uri(deckUri).accept(MediaType.APPLICATION_JSON).retrieve().body(ImportSwudb.class);
        final var mainCards = Bags.mutable.<Card.Id>ofInitialCapacity(50);
        final var sideboardCards = Bags.mutable.<Card.Id>ofInitialCapacity(10);

        for (final var entry : swudbDeck.shuffledDeck) {
            final var cardId = entry.card.toCardId();
            mainCards.addOccurrences(cardId, entry.count);
            sideboardCards.addOccurrences(cardId, entry.sideboardCount);
        }

        return new Deck(uri, swudbDeck.authorName, Format.PREMIER, swudbDeck.leader.toCardId(), swudbDeck.base.toCardId(),
                mainCards.toImmutableBag(), sideboardCards.toImmutableBag(), "0-0-0", List.of()
        );
    }

    private Deck.Match toDeckMatch(int tournamentId, MeleeDeckMatch m) {
        var record = "0-0-0";
        Deck.Match.Result result = Deck.Match.Result.UNKNOWN;

        if (m.result.contains("a bye")) {
            result = Deck.Match.Result.BYE;
            record = "1-0-0";
        } else if (m.result.contains("Not reported")) {
            result = Deck.Match.Result.UNKNOWN;
        } else if (m.result.contains("forfeited the match")) {
            result = Deck.Match.Result.DRAW;
            record = "0-0-1";
        } else {
            var score1 = 0;
            var score2 = 0;
            var score3 = 0;

            var scoreMatcher = SCORE_PATTERN.matcher(m.result);
            if (!scoreMatcher.find()) {
                scoreMatcher = SCORE_PATTERN2.matcher(m.result);
                if (!scoreMatcher.find()) {
                    throw new AppException("Unable to parse match record: " + m.result);
                } else {
                    score1 = Integer.parseInt(scoreMatcher.group(1));
                    score2 = Integer.parseInt(scoreMatcher.group(2));
                }
            } else {
                score1 = Integer.parseInt(scoreMatcher.group(1));
                score2 = Integer.parseInt(scoreMatcher.group(2));
                score3 = Integer.parseInt(scoreMatcher.group(3));
            }

            if (m.opponentPlayerName == null) {
                result = Deck.Match.Result.UNKNOWN;
            } else if (m.result.contains(" won ") && m.result.startsWith(m.opponentPlayerName)) {
                result = Deck.Match.Result.LOSS;
                record = "%d-%d-%d".formatted(score2, score1, score3);
            } else if (m.result.contains(" Draw")) {
                result = Deck.Match.Result.DRAW;
                record = "%d-%d-%d".formatted(score1, score2, score3);
            } else {
                result = Deck.Match.Result.WIN;
                record = "%d-%d-%d".formatted(score1, score2, score3);
            }
        }

        URI opponentDeck = null;
        if (!Deck.Match.Result.BYE.equals(result) && !Deck.Match.Result.UNKNOWN.equals(result)) {
            if (m.opponentDeck == null) {
                opponentDeck = findMeleeDeck(findRoundId(tournamentId), m.opponentPlayer);
            } else {
                opponentDeck = createMeleeDeckUri(m.opponentDeck);
            }
        }

        return new Deck.Match(
                m.round,
                m.opponentPlayer,
                opponentDeck,
                result,
                record
        );
    }

    private int findRoundId(int tournamentId) {
        final var uri = UriComponentsBuilder.fromUriString("https://melee.gg/Tournament/View/").path(String.valueOf(tournamentId)).toUriString();
        final var meleePage = client.get().uri(uri).retrieve().body(String.class);
        final var meleeDoc = Jsoup.parse(meleePage);
        final var standingsElem = meleeDoc.getElementById("standings-round-selector-container");
        final var roundStandingsElems = standingsElem.getElementsByAttributeValue("data-is-completed", "True");
        if (roundStandingsElems.isEmpty()) {
            throw new AppException("Unable to find round id in tournament page: " + uri);
        }

        final var lastRoundElem = roundStandingsElems.last();
        return Integer.parseInt(lastRoundElem.attr("data-id"));
    }

    private URI findMeleeDeck(int roundId, String username) {
        final var body = new LinkedMultiValueMap<String, String>();
        body.add("columns[0][data]", "Rank");
        body.add("columns[0][name]", "Rank");
        body.add("columns[0][searchable]", "true");
        body.add("columns[0][orderable]", "true");
        body.add("columns[0][search][value]", "");
        body.add("columns[0][search][regex]", "false");
        body.add("columns[1][data]", "Decklists");
        body.add("columns[1][name]", "Decklists");
        body.add("columns[1][searchable]", "false");
        body.add("columns[1][orderable]", "false");
        body.add("columns[1][search][value]", "");
        body.add("columns[1][search][regex]", "false");
        body.add("order[0][column]", "0");
        body.add("order[0][dir]", "asc");
        body.add("start", "0");
        body.add("length", "1");
        body.add("search[value]", username);
        body.add("search[regex]", "false");
        body.add("roundId", String.valueOf(roundId));

        final var resp = client.post()
                .uri("https://melee.gg/Standing/GetRoundStandings")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve().body(MeleeRoundStandings.class);
        if (resp.data.isEmpty() || resp.data.get(0).decklists.isEmpty()) {
            logger.warn("Unable to lookup deck from user {} in round {}", username, roundId);
            return null;
        }

        final var deckId = resp.data.get(0).decklists.get(0).decklistId;
        final var deckUrl = createMeleeDeckUri(deckId);
        logger.trace("Found deck from user {} in round {}: {}", username, roundId, deckUrl);
        return deckUrl;
    }

    private static URI createMeleeDeckUri(String meleeDeckId) {
        Assert.notNull(meleeDeckId, "Melee deck id must not be null");
        return UriComponentsBuilder.fromUriString("https://melee.gg/Decklist/View/").path(meleeDeckId).build().toUri();
    }

    private static String md5(String name) {
        return DigestUtils.md5DigestAsHex(name.getBytes(StandardCharsets.UTF_8));
    }

    private record MeleeDeckWrapper(
            @JsonProperty(required = true) @JsonAlias("Json") String json
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MeleeDeckDetails(
            @JsonProperty(required = true) @JsonAlias("Team") MeleeDeckTeam team,
            @JsonAlias("Matches") List<MeleeDeckMatch> matches
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MeleeDeckTeam(
            @JsonProperty(required = true) @JsonAlias("Username") String user,
            @JsonProperty(required = true) @JsonAlias("MatchRecord") String matchRecord
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MeleeDeckMatch(
            @JsonProperty(required = true) @JsonAlias("Round") int round,
            @JsonProperty(required = true) @JsonAlias("Opponent") String opponentPlayerName,
            @JsonProperty(required = true) @JsonAlias("OpponentUsername") String opponentPlayer,
            @JsonAlias("OpponentDecklistGuid") String opponentDeck,
            @JsonProperty(required = true) @JsonAlias("Result") String result
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VersionedDeck(
            @JsonProperty(required = true) int version
    ) {
    }

    private record MeleeRoundStandings(
            List<JsonPlayer> data
    ) {
    }

    private record JsonPlayer(
            @JsonAlias("Decklists") List<JsonPlayerDeck> decklists
    ) {
    }

    private record JsonPlayerDeck(
            @JsonAlias("DecklistId") String decklistId
    ) {
    }

    private record ImportSwudb(
            String authorName,
            ImportSwudbCard leader,
            ImportSwudbCard base,
            List<ImportSwudEntry> shuffledDeck
    ) {
    }

    private record ImportSwudbCard(
            String defaultExpansionAbbreviation,
            String defaultCardNumber
    ) {
        Card.Id toCardId() {
            return Card.Id.valueOf("%s-%s".formatted(defaultExpansionAbbreviation, defaultCardNumber));
        }
    }

    private record ImportSwudEntry(
            int count,
            int sideboardCount,
            ImportSwudbCard card
    ) {
    }
}

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

package net.swumeta.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheFormatter;
import io.jstach.jstache.JStacheFormatterTypes;
import io.jstach.jstachio.JStachio;
import net.swumeta.cli.*;
import net.swumeta.cli.model.*;
import net.swumeta.cli.statistics.CardStatisticsService;
import net.swumeta.cli.statistics.DeckStatisticsService;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.factory.Bags;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
class GenerateSiteCommand {
    private final Logger logger = LoggerFactory.getLogger(GenerateSiteCommand.class);
    private final MetagameService metagameService;
    private final EventService eventService;
    private final CardDatabaseService cardDatabaseService;
    private final DeckService deckService;
    private final DeckStatisticsService deckStatisticsService;
    private final CardStatisticsService cardStatisticsService;
    private final RedirectService redirectService;
    private final QuoteService quoteService;
    private final JStachio jStachio;
    private final AppConfig config;
    private final StaticResources staticResources;
    private final ObjectMapper objectMapper;

    GenerateSiteCommand(MetagameService metagameService, EventService eventService, CardDatabaseService cardDatabaseService, DeckService deckService, DeckStatisticsService deckStatisticsService, CardStatisticsService cardStatisticsService, RedirectService redirectService, QuoteService quoteService, AppConfig config, StaticResources staticResources) {
        this.metagameService = metagameService;
        this.eventService = eventService;
        this.deckService = deckService;
        this.cardDatabaseService = cardDatabaseService;
        this.deckStatisticsService = deckStatisticsService;
        this.cardStatisticsService = cardStatisticsService;
        this.redirectService = redirectService;
        this.quoteService = quoteService;
        this.config = config;
        this.staticResources = staticResources;
        this.jStachio = JStachio.of();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    void run() {
        logger.info("Generating website...");

        final var outputDir = config.output();
        logger.debug("Output directory: {}", outputDir);
        outputDir.mkdirs();

        try {
            staticResources.copyToDirectory(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy static resources", e);
        }

        final var aboutDir = new File(outputDir, "about");
        if (!aboutDir.exists()) {
            aboutDir.mkdirs();
        }
        renderToFile(new AboutModel(new HtmlMeta(
                "About", """
                Discover swumeta.net, created by Alexandre (NotAlex), software engineer and Star Wars Unlimited player, to analyze matchups and optimize your decks against popular game leaders.
                """,
                UriComponentsBuilder.fromUri(config.base()).path("/about/").build().toUri()),
                quoteService.randomQuote()), new File(aboutDir, "index.html"));
        renderToFile(new VersionModel(), new File(outputDir, "version.json"));

        final var metagame = metagameService.getMetagame();

        final var eventPages = new ArrayList<EventPage>(16);
        final var tournamentsDir = new File(outputDir, "tournaments");
        if (!tournamentsDir.exists()) {
            tournamentsDir.mkdirs();
        }
        for (final var event : eventService.list()) {
            logger.info("Processing event: {}", event);
            if (event.hidden()) {
                logger.debug("Skipping hidden event: {}", event);
                continue;
            }

            final var eventName = toLowercaseAscii(event.name());
            final var eventDirName = "%s/%02d/%02d/%s".formatted(event.date().getYear(), event.date().getMonthValue(), event.date().getDayOfMonth(), eventName);
            final var eventDir = new File(tournamentsDir, eventDirName);
            if (!eventDir.exists()) {
                eventDir.mkdirs();
            }
            final var countryFlag = event.location().countryFlag();
            final var videoLinks = event.links() != null
                    ? Lists.immutable.fromStream(event.links().stream().map(this::createVideoEmbedLink).filter(Objects::nonNull))
                    : Lists.immutable.<Link>empty();

            final var deckUris = Lists.immutable.fromStream(event.decks().stream()
                    .filter(d -> d.url() != null).map(Event.DeckEntry::url));
            final var deckBag = addMissingArchetypes(event, deckStatisticsService.getMostPlayedDecks(deckUris), 0);
            final var deckBagTop64 = addMissingArchetypes(event, deckStatisticsService.getMostPlayedDecks(deckUris.take(64)), 64);
            final var deckBagTop8 = addMissingArchetypes(event, deckStatisticsService.getMostPlayedDecks(deckUris.take(8)), 8);

            final var leaderSeries = toLeaderSerie(deckBag);
            final var leaderSeriesTop64 = toLeaderSerie(deckBagTop64);
            final var leaderSeriesTop8 = toLeaderSerie(deckBagTop8);

            final var statsFileName = "statistics.html";
            renderToFile(new EventStatsModel(new HtmlMeta(
                            "Statistics from " + event.name(),
                            "Statistics from the Star Wars Unlimited tournament " + event.name() + " taking place in " + event.location() + " on " + formatDate(event),
                            UriComponentsBuilder.fromUri(config.base()).path(eventDirName).path("/").path(statsFileName).build().toUri()),
                            event, countryFlag),
                    new File(eventDir, statsFileName));
            renderToFile(new KeyValueModel(leaderSeries), new File(eventDir, "all-leaders.json"));
            renderToFile(new KeyValueModel(leaderSeriesTop64), new File(eventDir, "top64-leaders.json"));
            renderToFile(new KeyValueModel(leaderSeriesTop8), new File(eventDir, "top8-leaders.json"));
            renderToFile(new KeyValueModel(computeSurvivorRates(leaderSeries, leaderSeriesTop64)), new File(eventDir, "top64-conversion.json"));
            renderToFile(new KeyValueModel(computeSurvivorRates(leaderSeries, leaderSeriesTop8)), new File(eventDir, "top8-conversion.json"));

            final var leaderMatchups = deckStatisticsService.getLeaderMatchups(
                    event.decks().stream().map(Event.DeckEntry::url).filter(Objects::nonNull).toList());
            renderToFile(toMinrateMatrixModel(leaderMatchups), new File(eventDir, "winrates-matrix.json"));
            renderToFile(toWinRateDataModel(leaderMatchups), new File(eventDir, "winrates-chart.json"));

            int winLossCount = 0;
            int drawCount = 0;
            for (final var entry : event.decks()) {
                if (entry.url() == null) {
                    continue;
                }
                try {
                    final var deck = deckService.load(entry.url());
                    for (final var m : deck.matches()) {
                        if (Deck.Match.Result.WIN.equals(m.result()) || Deck.Match.Result.LOSS.equals(m.result())) {
                            winLossCount += 1;
                        } else if (Deck.Match.Result.DRAW.equals(m.result())) {
                            drawCount += 1;
                        }
                    }
                } catch (AppException ignore) {
                }
            }
            renderToFile(new KeyValueModel(Lists.immutable.of(
                    new KeyValue("Wins or Losses", winLossCount),
                    new KeyValue("Draws", drawCount)
            )), new File(eventDir, "match-results.json"));

            final var decks = Lists.immutable.fromStream(event.decks().stream()
                    .map(this::toDeckWithRank)
                    .filter(Objects::nonNull)
                    .sorted()
            );
            final var leaderBag = nMostCards(Bags.immutable.fromStream(deckBag.stream().map(DeckArchetype::leader).map(deckService::formatLeader)), 4);
            final var baseBag = nMostCards(Bags.immutable.fromStream(deckBag.stream().map(deckService::lookupBase).map(deckService::formatBase)), 4);
            renderToFile(new EventModel(new HtmlMeta(event.name(),
                            "Results from the Star Wars Unlimited tournament " + event.name() + " taking place in " + event.location() + " on " + formatDate(event) + ", including standings, decklists, Melee.gg link and more",
                            UriComponentsBuilder.fromUri(config.base()).path("/%s/%s/".formatted(tournamentsDir.getName(), eventDirName)).build().toUri()),
                            event, countryFlag, "/%s/%s/%s".formatted(tournamentsDir.getName(), eventDirName, statsFileName),
                            decks, !decks.isEmpty(), videoLinks),
                    new File(eventDir, "index.html"));
            renderToFile(new KeyValueModel(leaderBag), new File(eventDir, "usage-leaders.json"));
            renderToFile(new KeyValueModel(baseBag), new File(eventDir, "usage-bases.json"));
            eventPages.add(new EventPage(event, isEventNew(event), metagame.events().contains(event),
                    getEventWinner(event), countryFlag, "/%s/%s/".formatted(tournamentsDir.getName(), eventDirName)));
        }

        logger.info("Processing event index page");
        Collections.sort(eventPages, Comparator.reverseOrder());
        renderToFile(new EventIndexModel(new HtmlMeta("Tournaments",
                        "Star Wars Unlimited tournaments (Planetary Qualifier, Sector Qualifier, Regional Qualifier, Galactic Championship)",
                        UriComponentsBuilder.fromUri(config.base()).path("/tournaments/").build().toUri()), eventPages),
                new File(outputDir, "/tournaments/index.html"));

        logger.info("Processing metagame page");
        final var cardBag = cardStatisticsService.getMostPlayedCards(metagame.decks());
        final var deckBag = cardStatisticsService.getMostPlayedCards(metagame.decks(), c -> c.type().equals(Card.Type.LEADER));

        final int totalDecks = deckBag.size();
        final int totalCards = cardBag.size();
        final var topDecks = Lists.immutable.fromStream(deckBag.topOccurrences(5).stream()
                .limit(5)
                .map(e -> new KeyValue(cardDatabaseService.formatName(e.getOne()), (int) Math.round((100d * e.getTwo() / totalDecks))))
                .sorted(Comparator.reverseOrder()));
        final var topCards = Lists.immutable.fromStream(cardBag.topOccurrences(5).stream()
                .limit(5)
                .map(e -> new KeyValue(cardDatabaseService.findById(e.getOne()).name(), (int) Math.round((100d * e.getTwo() / totalCards))))
                .sorted(Comparator.reverseOrder()));

        renderToFile(new IndexModel(new HtmlMeta(config.base()),
                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).format(metagame.date()),
                        totalDecks, topDecks, topCards),
                new File(outputDir, "index.html"));

        logger.info("Processing matchups based on {} decks", metagame.decks().size());
        final var matchups = deckStatisticsService.getLeaderMatchups(metagame.decks());
        var matchCount = 0;
        for (final var m : matchups) {
            matchCount += m.matchCount();
        }

        final var metaDir = new File(outputDir, "meta");
        final var winRatesDir = new File(metaDir, "win-rates");
        if (!winRatesDir.exists()) {
            winRatesDir.mkdirs();
        }

        final var metaHeader = new MetaHeader(metagame, matchCount);
        renderToFile(new MetaWinratesModel(new HtmlMeta("Metagame - Win Rates", """
                Win rates report for the Star Wars Unlimited card game, including the meta share, based on the most played deck archetypes in major tournaments
                """, UriComponentsBuilder.fromUri(config.base()).path("/meta/win-rates/").build().toUri()),
                metaHeader), new File(winRatesDir, "index.html"));
        renderToFile(toMinrateMatrixModel(matchups), new File(winRatesDir, "winrates-matrix.json"));
        renderToFile(toWinRateDataModel(matchups), new File(winRatesDir, "winrates-chart.json"));

        final var top8Leaders = Bags.immutable.fromStream(
                metagame.events().stream().flatMap(e -> e.decks().stream())
                        .filter(e -> e.rank() < 9)
                        .map(e -> {
                            if (e.url() != null) {
                                return deckService.load(e.url()).leader();
                            }
                            return e.leader();
                        }).filter(Objects::nonNull).map(deckService::formatLeader));
        final var top8LeadersWinners = Bags.immutable.fromStream(
                metagame.events().stream().flatMap(e -> e.decks().stream())
                        .filter(e -> e.rank() == 1)
                        .map(e -> {
                            if (e.url() != null) {
                                return deckService.load(e.url()).leader();
                            }
                            return e.leader();
                        }).filter(Objects::nonNull).map(deckService::formatLeader));
        final var top8Bases = Bags.immutable.fromStream(
                metagame.events().stream().flatMap(e -> e.decks().stream())
                        .filter(e -> e.rank() < 9)
                        .map(e -> {
                            if (e.url() != null) {
                                return deckService.load(e.url()).base();
                            }
                            return e.base();
                        }).filter(Objects::nonNull).map(deckService::formatBase));
        final var top8Archetypes = Bags.immutable.fromStream(
                metagame.events().stream().flatMap(e -> e.decks().stream())
                        .filter(e -> e.rank() < 9)
                        .map(e -> {
                            if (e.url() != null) {
                                final var deck = deckService.load(e.url());
                                return deckService.getArchetype(deck);
                            }
                            if (e.leader() != null && e.base() != null) {
                                return DeckArchetype.valueOf(e.leader(), e.base());
                            }
                            return null;
                        }).filter(Objects::nonNull).map(deckService::formatArchetype));
        final var top8ArchetypesWinners = Bags.immutable.fromStream(
                metagame.events().stream().flatMap(e -> e.decks().stream())
                        .filter(e -> e.rank() == 1)
                        .map(e -> {
                            if (e.url() != null) {
                                final var deck = deckService.load(e.url());
                                return deckService.getArchetype(deck);
                            }
                            if (e.leader() != null && e.base() != null) {
                                return DeckArchetype.valueOf(e.leader(), e.base());
                            }
                            return null;
                        }).filter(Objects::nonNull).map(deckService::formatArchetype));
        final var top8LeadersCosts = Bags.immutable.fromStream(
                metagame.events().stream().flatMap(e -> e.decks().stream())
                        .filter(e -> e.rank() < 9)
                        .map(e -> {
                            if (e.url() != null) {
                                return deckService.load(e.url()).leader();
                            }
                            return e.leader();
                        }).filter(Objects::nonNull).map(cardDatabaseService::findById).map(c -> "Cost " + c.cost()));
        final var top8Dir = new File(metaDir, "top8");
        if (!top8Dir.exists()) {
            top8Dir.mkdirs();
        }
        renderToFile(new MetaTop8Model(new HtmlMeta("Metagame - Top 8", """
                Statistics about Top 8 decks of the Star Wars Unlimited metagame
                """, UriComponentsBuilder.fromUri(config.base()).path("/meta/top8/").build().toUri()),
                metaHeader), new File(top8Dir, "index.html"));
        renderToFile(new KeyValueModel(toSeries(top8Leaders)), new File(top8Dir, "top8-leaders.json"));
        renderToFile(new KeyValueModel(toSeries(top8LeadersWinners)), new File(top8Dir, "top8-leaders-winners.json"));
        renderToFile(new KeyValueModel(toSeries(top8Bases)), new File(top8Dir, "top8-bases.json"));
        renderToFile(new KeyValueModel(toSeries(top8Archetypes)), new File(top8Dir, "top8-archetypes.json"));
        renderToFile(new KeyValueModel(toSeries(top8ArchetypesWinners)), new File(top8Dir, "top8-archetypes-winners.json"));
        renderToFile(new KeyValueModel(toSeries(top8LeadersCosts)), new File(top8Dir, "top8-leaders-costs.json"));

        logger.info("Processing redirects");
        for (final var redirect : redirectService.getRedirects()) {
            final var resFile = new File(outputDir, redirect.resource().endsWith("/") ? (redirect.resource() + "index.html") : redirect.resource());
            if (!resFile.getParentFile().exists()) {
                resFile.getParentFile().mkdirs();
            }
            renderToFile(new RedirectModel(redirect.target()), resFile);
        }
        generateSitemap(outputDir, Set.of());
    }

    private ImmutableBag<DeckArchetype> addMissingArchetypes(Event event, Bag<DeckArchetype> existing, int limit) {
        final var moreArchetypes = Bags.mutable.withAll(existing);
        for (final var e : event.decks()) {
            if (limit != 0 && e.rank() > limit) {
                continue;
            }
            if (e.url() == null && e.leader() != null && e.base() != null) {
                final var archetype = DeckArchetype.valueOf(e.leader(), e.base());
                moreArchetypes.add(archetype);
            }
        }
        return moreArchetypes.toImmutable();
    }

    private DeckWithRank toDeckWithRank(Event.DeckEntry e) {
        if (e.url() == null && e.leader() != null && e.base() != null && e.player() != null) {
            final var archetype = DeckArchetype.valueOf(e.leader(), e.base());
            final var name = deckService.formatArchetype(archetype);
            final var leaderCard = cardDatabaseService.findById(e.leader());
            final var baseCard = cardDatabaseService.findById(e.base());
            final var aspects = Lists.mutable.<Card.Aspect>fromStream(
                    Stream.concat(leaderCard.aspects().stream(), baseCard.aspects().stream())).toSortedList();
            return new DeckWithRank(e.rank(), e.pending(), null, e.player(), name, leaderCard, baseCard, aspects, formatMatchRecord(null), null);
        }
        if (e.url() == null) {
            return null;
        }

        final Deck deck;
        try {
            deck = deckService.load(e.url());
        } catch (AppException ignore) {
            return null;
        }
        return new DeckWithRank(
                e.rank(), e.pending(), deck, deck.player(), deckService.formatName(deck),
                cardDatabaseService.findById(deck.leader()),
                cardDatabaseService.findById(deck.base()),
                getAspects(deck),
                formatMatchRecord(deck.matchRecord()),
                deckService.toSwudbJson(deck)
        );
    }

    private static String formatMatchRecord(String s) {
        if (s == null || "0-0-0".equals(s)) {
            return "N/A";
        }
        return s;
    }

    private ImmutableList<KeyValue> toLeaderSerie(Bag<DeckArchetype> bag) {
        final var keyValues = Lists.mutable.<KeyValue>ofInitialCapacity(bag.sizeDistinct());
        bag.groupBy(DeckArchetype::leader).forEachKeyMultiValues((Procedure2<Card.Id, RichIterable<DeckArchetype>>) (leader, archetypes) -> {
            final var card = cardDatabaseService.findById(leader);
            keyValues.add(new KeyValue("%s (%s)".formatted(card.name().replace("\"", " "), card.set()), archetypes.size()));
        });
        return keyValues.toImmutableSortedList();
    }

    private ImmutableList<KeyValue> toSeries(Bag<String> bag) {
        return bag.toMapOfItemToCount()
                .keyValuesView()
                .collect(pair -> new KeyValue(pair.getOne(), pair.getTwo()))
                .toSortedList((kv1, kv2) -> Integer.compare(kv2.value(), kv1.value())).reverseThis().toImmutableList();
    }

    private ImmutableList<KeyValue> computeSurvivorRates(ImmutableList<KeyValue> leaders, ImmutableList<KeyValue> survivorBracket) {
        final var survivorSeries = Lists.mutable.<KeyValue>ofInitialCapacity(survivorBracket.size());
        for (final var kv : leaders) {
            final var kv2Opt = survivorBracket.stream().filter(kv2 -> kv2.key.equals(kv.key)).findFirst();
            if (kv2Opt.isEmpty()) {
                continue;
            }
            final var kv2 = kv2Opt.get();
            final var rate = (int) Math.round(100d * kv2.value / kv.value);
            survivorSeries.add(new KeyValue(kv.key, rate));
        }
        return survivorSeries.sortThis().toImmutable();
    }

    private ImmutableList<KeyValue> nMostCards(Bag<String> bag, int n) {
        final var allItemsRanked = bag.topOccurrences(bag.sizeDistinct());
        final var result = UnifiedMap.<String, Integer>newMap();
        final int topCount = Math.min(n, allItemsRanked.size());
        for (int i = 0; i < topCount; i++) {
            final var pair = allItemsRanked.get(i);
            result.put(pair.getOne(), pair.getTwo());
        }
        int othersTotal = 0;
        for (int i = topCount; i < allItemsRanked.size(); i++) {
            othersTotal += allItemsRanked.get(i).getTwo();
        }
        if (othersTotal > 0) {
            result.put("Others", othersTotal);
        }
        return Lists.immutable.fromStream(result.entrySet().stream().map(e -> new KeyValue(e.getKey(), e.getValue())).sorted());
    }

    record HtmlMeta(
            String title,
            String description,
            URI canonicalUrl
    ) implements TemplateSupport {
        HtmlMeta(URI canonicalUrl) {
            this(null, null, canonicalUrl);
        }
    }

    @JStache(path = "/templates/index.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record IndexModel(HtmlMeta meta, String lastEventDate, int totalDecks,
                      ImmutableList<KeyValue> topDecks, ImmutableList<KeyValue> topCards) implements TemplateSupport {
    }

    @JStache(path = "/templates/about.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record AboutModel(HtmlMeta meta, String quote) implements TemplateSupport {
    }

    @JStache(path = "/templates/version.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record VersionModel() implements TemplateSupport {
    }

    @JStache(path = "/templates/events.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record EventIndexModel(HtmlMeta meta, List<EventPage> events) implements TemplateSupport {
    }

    record EventPage(
            Event event, boolean newLabel, boolean metaRelevant, EventWinner winner, String countryFlag, String page
    ) implements Comparable<EventPage> {
        @Override
        public int compareTo(EventPage o) {
            return event.compareTo(o.event);
        }
    }

    record EventWinner(
            Icon leader,
            Icon base
    ) {
    }

    record Icon(
            URI url,
            String alt
    ) {
    }

    @JStache(path = "/templates/event.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record EventModel(HtmlMeta meta, Event event, String countryFlag,
                      String statsPage,
                      ImmutableList<DeckWithRank> decks, boolean hasDecks,
                      ImmutableList<Link> videoLinks) implements TemplateSupport {
    }

    record KeyValue(
            String key,
            int value
    ) implements Comparable<KeyValue> {
        @Override
        public int compareTo(KeyValue o) {
            if ("Others".equals(key)) {
                return -1;
            }
            if ("Others".equals(o.key)) {
                return 1;
            }
            if (value != o.value) {
                return value < o.value ? -1 : 1;
            }
            return key.compareTo(o.key);
        }
    }

    @JStache(path = "/templates/kv.mustache")
    record KeyValueModel(ImmutableList<KeyValue> series) implements TemplateSupport {
    }

    record DeckWithRank(int rank, boolean pending, Deck deck, String player, String name, Card leader, Card base,
                        Iterable<Card.Aspect> aspects, String matchRecord,
                        String swudbFormat) implements Comparable<DeckWithRank> {
        @Override
        public int compareTo(DeckWithRank o) {
            if (rank == o.rank) {
                return name.compareTo(o.name);
            }
            return rank < o.rank ? -1 : 1;
        }
    }

    @JStache(path = "/templates/event-stats.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record EventStatsModel(HtmlMeta meta, Event event, String countryFlag) implements TemplateSupport {
    }

    @JStache(path = "/templates/meta-winrates.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record MetaWinratesModel(HtmlMeta meta, MetaHeader header) implements TemplateSupport {
    }

    @JStache(path = "/templates/meta-top8.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record MetaTop8Model(HtmlMeta meta, MetaHeader header) implements TemplateSupport {
    }

    record MetaHeader(
            MetagameService.Metagame metagame,
            int _matchCount
    ) {
        String deckCount() {
            final var nf = NumberFormat.getIntegerInstance(Locale.ENGLISH);
            return nf.format(metagame.decks().size());
        }

        String matchCount() {
            final var nf = NumberFormat.getIntegerInstance(Locale.ENGLISH);
            return nf.format(_matchCount);
        }

        String eventCount() {
            final var nf = NumberFormat.getIntegerInstance(Locale.ENGLISH);
            return nf.format(metagame.events().size());
        }
    }

    interface TemplateSupport {
        default int year() {
            return LocalDate.now().getYear();
        }

        default ZonedDateTime now() {
            return ZonedDateTime.now();
        }
    }

    private void renderToFile(Object model, File output) {
        logger.info("Generating file: {}", output.getName());
        final var content = jStachio.execute(model);
        try (final var out = new FileWriter(output, StandardCharsets.UTF_8)) {
            FileCopyUtils.copy(content, out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to render file: " + output, e);
        }
    }

    private static void listFilesRecursively(File directory, List<File> filesList, String extension) {
        final var files = directory.listFiles();
        if (files != null) {
            final var dotExt = extension.startsWith(".") ? extension : ("." + extension);
            for (final var file : files) {
                if (file.isFile() && file.getName().endsWith(dotExt)) {
                    filesList.add(file);
                } else if (file.isDirectory()) {
                    listFilesRecursively(file, filesList, extension);
                }
            }
        }
    }

    private List<Card.Aspect> getAspects(Deck d) {
        final var aspects = new ArrayList<Card.Aspect>(3);
        final var leader = cardDatabaseService.findById(d.leader());
        if (leader.aspects() != null) {
            aspects.addAll(leader.aspects());
        }
        final var base = cardDatabaseService.findById(d.base());
        if (base.aspects() != null) {
            for (final var a : base.aspects()) {
                aspects.add(a);
            }
        }
        Collections.sort(aspects);
        return aspects;
    }

    @JStacheFormatter
    @JStacheFormatterTypes(types = {Location.class, Event.Type.class, LocalDate.class, ZonedDateTime.class, Card.Aspect.class, net.swumeta.cli.model.Set.class, CharSequence.class})
    static class CustomFormatter {
        public static Function<Object, String> provider() {
            return o -> {
                if (o instanceof Location location) {
                    if (location.city() == null) {
                        return location.country();
                    }
                    return "%s, %s".formatted(location.city(), location.country());
                }
                if (o instanceof Event.Type type) {
                    return switch (type) {
                        case Event.Type.GS -> "Galactic Championship";
                        case Event.Type.PQ -> "Planetary Qualifier";
                        case Event.Type.RQ -> "Regional Qualifier";
                        case Event.Type.SQ -> "Sector Qualifier";
                        case Event.Type.MAJOR -> "Major Tournament";
                        case Event.Type.MINOR -> "Minor Tournament";
                    };
                }
                if (o instanceof LocalDate d) {
                    return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
                }
                if (o instanceof ZonedDateTime dt) {
                    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dt);
                }
                if (o instanceof Card.Aspect aspect) {
                    return switch (aspect) {
                        case HEROISM -> "aspect_heroism.png";
                        case VILLAINY -> "aspect_villainy.png";
                        case VIGILANCE -> "aspect_vigilance.png";
                        case COMMAND -> "aspect_command.png";
                        case AGGRESSION -> "aspect_aggression.png";
                        case CUNNING -> "aspect_cunning.png";
                    };
                }
                if (o instanceof net.swumeta.cli.model.Set) {
                    return ((net.swumeta.cli.model.Set) o).name();
                }
                if (o instanceof String) {
                    return (String) o;
                }
                if (o instanceof CharSequence) {
                    return ((CharSequence) o).toString();
                }
                if (o instanceof URI) {
                    return ((URI) o).toASCIIString();
                }
                throw new RuntimeException("Unable to format instance: " + o);
            };
        }
    }

    private URI createTwitchEmbedLink(URI uri) {
        final var uriBuilder = UriComponentsBuilder.fromUriString("https://player.twitch.tv/")
                .queryParam("parent", config.base().getHost())
                .queryParam("autoplay", "false");

        final var inputUri = UriComponentsBuilder.fromUri(uri).build();
        final var qParams = inputUri.getQueryParams();
        final var pathSegments = inputUri.getPathSegments();
        if (qParams.containsKey("channel")) {
            uriBuilder.queryParam("channel", qParams.getFirst("channel"));
        } else if (qParams.containsKey("videos")) {
            uriBuilder.queryParam("video", qParams.getFirst("videos"));
        } else if (pathSegments.size() > 1 && pathSegments.get(0).equals("channel")) {
            uriBuilder.queryParam("channel", pathSegments.get(1));
        } else if (pathSegments.size() > 1 && pathSegments.get(0).equals("videos")) {
            uriBuilder.queryParam("video", pathSegments.get(1));
        } else if (pathSegments.size() == 1) {
            uriBuilder.queryParam("channel", pathSegments.get(0));
        } else {
            throw new RuntimeException("Invalid Twitch URL: " + uri);
        }
        return uriBuilder.build().toUri();
    }

    private URI createYoutubeEmbedLink(URI uri) {
        // Different YouTube URL patterns
        final String[] patterns = {
                "(?<=watch\\?v=)[a-zA-Z0-9_-]+",            // Standard: https://www.youtube.com/watch?v=VIDEO_ID
                "(?<=youtu.be/)[a-zA-Z0-9_-]+",             // Shortened: https://youtu.be/VIDEO_ID
                "(?<=embed/)[a-zA-Z0-9_-]+",                // Already embedded: https://www.youtube.com/embed/VIDEO_ID
                "(?<=live/)[a-zA-Z0-9_-]+",                 // Live: https://www.youtube.com/live/VIDEO_ID
                "(?<=v/)[a-zA-Z0-9_-]+",                    // Old format: https://www.youtube.com/v/VIDEO_ID
                "(?<=youtube.com/shorts/)[a-zA-Z0-9_-]+"    // Shorts format: https://www.youtube.com/shorts/VIDEO_ID
        };
        final var youtubeUrl = uri.toASCIIString();
        String embedId = null;
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(youtubeUrl);
            if (matcher.find()) {
                embedId = matcher.group();
                break;
            }
        }
        if (embedId == null) {
            return uri;
        }
        return UriComponentsBuilder.fromUriString("https://www.youtube.com/embed/")
                .pathSegment(embedId).queryParam("autoplay", "0").build().toUri();
    }

    private Link createVideoEmbedLink(Link link) {
        if (link.url().getHost().contains("twitch.tv")) {
            return new Link(createTwitchEmbedLink(link.url()), link.title());
        } else if (link.url().getHost().contains("youtube.com") || link.url().getHost().contains("youtu.be")) {
            return new Link(createYoutubeEmbedLink(link.url()), link.title());
        }
        return null;
    }

    private EventWinner getEventWinner(Event event) {
        final var opt = event.decks().stream()
                .filter(e -> e.rank() == 1 && !e.pending())
                .findFirst();
        if (opt.isEmpty()) {
            return null;
        }

        final var e = opt.get();
        Card.Id leaderId = e.leader();
        Card.Id baseId = e.base();
        String leaderName = null;
        String baseName = null;

        if (leaderId != null) {
            leaderName = deckService.formatLeader(leaderId);
        }
        if (baseId != null) {
            baseName = deckService.formatBase(baseId);
        }

        if (leaderId == null || baseId == null) {
            if (e.url() == null) {
                return null;
            }
            final var deck = deckService.load(e.url());
            if (deck.leader() == null || deck.base() == null) {
                return null;
            }
            leaderId = deck.leader();
            baseId = deck.base();
            leaderName = deckService.formatLeader(deck);
            baseName = deckService.formatBase(deck);
        }
        if (leaderName == null || baseName == null) {
            return null;
        }
        final var leader = new Icon(cardDatabaseService.findById(leaderId).thumbnail(), leaderName);
        final var base = new Icon(cardDatabaseService.findById(baseId).thumbnail(), baseName);
        return new EventWinner(leader, base);
    }

    private void generateSitemap(File outputDir, Set<File> excludedFiles) {
        final var htmlFiles = new ArrayList<File>(32);
        listFilesRecursively(outputDir, htmlFiles, ".html");
        htmlFiles.removeAll(excludedFiles);

        final var sitemapEntries = new ArrayList<SitemapEntry>(htmlFiles.size());
        for (final var htmlFile : htmlFiles) {
            var res = outputDir.toPath().relativize(htmlFile.toPath()).toString().replace(File.separator, "/");
            if ("index.html".equals(res)) {
                continue;
            }
            if (res.endsWith("/index.html")) {
                res = res.replace("/index.html", "/");
            }
            final var uriBuilder = UriComponentsBuilder.fromUri(config.base())
                    .pathSegment(res.split("/"));
            if (res.endsWith("/")) {
                uriBuilder.path("/");
            }
            final var uri = uriBuilder.build().toUri();
            sitemapEntries.add(new SitemapEntry(uri, getLastModified(htmlFile)));
        }
        sitemapEntries.add(new SitemapEntry(config.base(),
                getLastModified(new File(outputDir, "index.html"))));

        logger.debug("Generating sitemap: {}", sitemapEntries);
        Collections.sort(sitemapEntries);
        renderToFile(new SitemapModel(sitemapEntries), new File(outputDir, "sitemap.xml"));

        final var sitemapUri = UriComponentsBuilder.fromUri(config.base()).path("sitemap.xml").build().toUri();
        logger.debug("Generating robots.txt: sitemap={}", sitemapUri);
        renderToFile(new RobotsModel(sitemapUri), new File(outputDir, "robots.txt"));
    }

    private static ZonedDateTime getLastModified(File f) {
        return Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault());
    }

    @JStache(path = "/templates/sitemap.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record SitemapModel(
            List<SitemapEntry> entries
    ) {
    }

    record SitemapEntry(
            URI url,
            ZonedDateTime lastModified
    ) implements Comparable<SitemapEntry> {
        @Override
        public int compareTo(SitemapEntry o) {
            if (url.compareTo(o.url) != 0) {
                return url.compareTo(o.url);
            }
            return lastModified.compareTo(o.lastModified);
        }
    }

    @JStache(path = "/templates/robots.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record RobotsModel(
            URI sitemap
    ) {
    }

    @JStache(path = "/templates/redirect.mustache")
    @JStacheConfig
    record RedirectModel(
            URI target
    ) implements TemplateSupport {
    }

    @JStache(path = "/templates/winrates-data.mustache")
    @JStacheConfig
    record WinrateDataModel(
            ImmutableList<WinrateDataEntry> series
    ) {
    }

    record WinrateDataEntry(
            String leader,
            int winRate,
            String metaShare,
            int matches,
            int winCount,
            int lossCount,
            int drawCount
    ) implements Comparable<WinrateDataEntry> {
        @Override
        public int compareTo(WinrateDataEntry o) {
            if (leader.equals(o.leader)) {
                return winRate > o.winRate ? -1 : 1;
            }
            return leader.compareTo(o.leader);
        }
    }

    @JStache(path = "/templates/winrates.mustache")
    @JStacheConfig
    record WinrateMatrixModel(
            ImmutableList<WinrateMatrixEntry> entries
    ) {
    }

    record WinrateMatrixEntry(
            WinrateMatrixLeader leader,
            ImmutableList<WinrateMatrixOpponent> opponents
    ) {
    }

    record WinrateMatrixLeader(
            String name,
            URI art
    ) {
    }

    record WinrateMatrixOpponent(
            String name,
            int winRate,
            int matches,
            int winCount,
            int lossCount,
            int drawCount
    ) {
    }

    private WinrateMatrixModel toMinrateMatrixModel(ImmutableList<DeckStatisticsService.LeaderMatchup> leaderMatchups) {
        final var nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
        nf.setMaximumFractionDigits(1);
        return new WinrateMatrixModel(Lists.immutable.fromStream(leaderMatchups.stream().map(matchup -> new WinrateMatrixEntry(
                new WinrateMatrixLeader(
                        cardDatabaseService.formatName(matchup.leader()),
                        cardDatabaseService.findById(matchup.leader()).thumbnail()
                ),
                Lists.immutable.fromStream(matchup.opponents().stream().map(op -> new WinrateMatrixOpponent(
                        cardDatabaseService.formatName(op.opponent()),
                        (int) Math.round(100d * op.winRate()),
                        op.matchCount(),
                        op.matchCount(Deck.Match.Result.WIN),
                        op.matchCount(Deck.Match.Result.LOSS),
                        op.matchCount(Deck.Match.Result.DRAW)
                )))
        ))));
    }

    private WinrateDataModel toWinRateDataModel(ImmutableList<DeckStatisticsService.LeaderMatchup> leaderMatchups) {
        final var nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
        nf.setMaximumFractionDigits(1);
        return new WinrateDataModel(Lists.immutable.fromStream(leaderMatchups.stream().map(matchup ->
                new WinrateDataEntry(cardDatabaseService.formatName(matchup.leader()),
                        (int) Math.round(100d * matchup.winRate()), nf.format(100d * matchup.metaShare()),
                        matchup.matchCount(), matchup.matchCount(Deck.Match.Result.WIN), matchup.matchCount(Deck.Match.Result.LOSS),
                        matchup.matchCount(Deck.Match.Result.DRAW))).sorted())
        );
    }

    private String formatDate(Event e) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.ENGLISH).format(e.date());
    }

    private boolean isEventNew(Event e) {
        return !e.hidden() && hasDecks(e) && LocalDate.now().minusDays(3).isBefore(e.date());
    }

    private boolean hasDecks(Event e) {
        for (final var deck : e.decks()) {
            if (deck.url() != null) {
                return true;
            }
        }
        return false;
    }

    private static String toLowercaseAscii(String s) {
        final var lowercase = s.toLowerCase(Locale.ENGLISH);
        final var normalized = Normalizer.normalize(lowercase, Normalizer.Form.NFD);
        return normalized.replaceAll("[^\\p{ASCII}]", "")
                .replace("$", "")
                .replace(" ", "-");
    }
}

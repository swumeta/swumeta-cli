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
import net.swumeta.cli.AppConfig;
import net.swumeta.cli.CardDatabaseService;
import net.swumeta.cli.DeckService;
import net.swumeta.cli.EventService;
import net.swumeta.cli.model.*;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.primitive.ObjectIntPair;
import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.list.mutable.FastList;
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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class GenerateSiteCommand {
    private final Logger logger = LoggerFactory.getLogger(GenerateSiteCommand.class);
    private final CardDatabaseService cardDatabaseService;
    private final EventService eventService;
    private final DeckService deckService;
    private final JStachio jStachio;
    private final AppConfig config;
    private final StaticResources staticResources;
    private final ObjectMapper objectMapper;

    GenerateSiteCommand(CardDatabaseService cardDatabaseService, EventService eventService, DeckService deckService, AppConfig config, StaticResources staticResources) {
        this.eventService = eventService;
        this.deckService = deckService;
        this.cardDatabaseService = cardDatabaseService;
        this.config = config;
        this.staticResources = staticResources;
        this.jStachio = JStachio.of();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    void run(File outputDir) {
        logger.info("Generating website...");

        logger.debug("Output directory: {}", outputDir);
        outputDir.mkdirs();

        try {
            staticResources.copyToDirectory(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy static resources", e);
        }

        renderToFile(new AboutModel("About"), new File(outputDir, "about.html"));
        renderToFile(new VersionModel(), new File(outputDir, "version.json"));

        final var dbDir = config.database();
        final var eventFiles = new ArrayList<File>(16);
        listFilesRecursively(new File(dbDir, "events"), eventFiles);

        LocalDate lastEventDate = null;
        final MutableBag<String> deckBag = HashBag.newBag(128);
        final MutableBag<String> deckTop8Bag = HashBag.newBag(128);
        final MutableBag<String> cardBag = HashBag.newBag(256);

        final var eventPages = new ArrayList<EventPage>(eventFiles.size());
        for (final var eventFile : eventFiles) {
            final var event = eventService.load(eventFile.toURI());

            if (lastEventDate == null || event.date().isAfter(lastEventDate)) {
                lastEventDate = event.date();
            }

            final List<DeckWithRank> decks;
            final MutableBag<String> leaderBag = HashBag.newBag();
            final MutableBag<String> baseBag = HashBag.newBag();
            if (event.decks() == null) {
                decks = List.of();
            } else {
                FastList.newList(event.decks()).take(8).stream()
                        .map(d -> deckService.load(d.url()).name()).forEach(deckTop8Bag::add);
                decks = event.decks().stream()
                        .filter(d -> d.url() != null)
                        .map(d -> {
                            try {
                                final var deck = deckService.load(d.url());
                                if (deck.isValid()) {
                                    leaderBag.add(deck.formatLeader().replace("'", " "));
                                    baseBag.add(deck.formatBase());
                                    deckBag.add(deck.name());

                                    for (final var c : deck.main()) {
                                        cardBag.add(c.name());
                                    }
                                    if (deck.sideboard() != null) {
                                        for (final var c : deck.sideboard()) {
                                            cardBag.add(c.name());
                                        }
                                    }

                                    return new DeckWithRank(d.rank(), deck, getAspects(deck), deck.toSwudbJson(objectMapper));
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to load deck: {}", d.url(), e);
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(DeckWithRank::rank))
                        .toList();
            }
            final var eventFileName = eventFile.getName().toLowerCase()
                    .replace(".yaml", "")
                    .replace(" ", "-") + ".html";
            final var countryFlag = getCountryCodeFromName(event.location().country());
            final List<Link> twitchLinks = event.links() != null ?
                    event.links().stream()
                            .filter(link -> link.url().getHost().contains("twitch.tv"))
                            .map(link -> new Link(createTwitchEmbedLink(link.url()), link.title()))
                            .toList()
                    : List.of();
            final List<Link> ytLinks = event.links() != null ?
                    event.links().stream()
                            .filter(link -> link.url().getHost().contains("youtube.com"))
                            .map(link -> new Link(createYoutubeEmbedLink(link.url()), link.title()))
                            .toList()
                    : List.of();
            final List<Link> videoLinks = Lists.mutable.withAll(twitchLinks).withAll(ytLinks);

            final var leaderSerie = new ArrayList<KeyValue>(leaderBag.size());
            leaderBag.forEachWithOccurrences((ObjectIntProcedure<String>) (name, count) -> leaderSerie.add(new KeyValue(name, count)));

            final MutableBag<String> top64LeaderBag = HashBag.newBag(64);
            Lists.immutable.withAll(event.decks()).take(64).stream()
                    .filter(d -> d.url() != null)
                    .map(link -> deckService.load(link.url()).formatLeader().replace("'", " "))
                    .forEach(top64LeaderBag::add);
            final var top64LeaderSerie = new ArrayList<KeyValue>(top64LeaderBag.size());
            top64LeaderBag.forEachWithOccurrences((ObjectIntProcedure<String>) (name, count) -> top64LeaderSerie.add(new KeyValue(name, count)));

            final MutableBag<String> top8LeaderBag = HashBag.newBag(8);
            Lists.immutable.withAll(event.decks()).take(8).stream()
                    .filter(d -> d.url() != null)
                    .map(link -> deckService.load(link.url()).formatLeader().replace("'", " "))
                    .forEach(top8LeaderBag::add);
            final var top8LeaderSerie = new ArrayList<KeyValue>(top8LeaderBag.size());
            top8LeaderBag.forEachWithOccurrences((ObjectIntProcedure<String>) (name, count) -> top8LeaderSerie.add(new KeyValue(name, count)));

            final var statsFileName = eventFileName.replace(".html", "-stats.html");
            renderToFile(new EventStatsModel("Statistics from " + event.name(), event, countryFlag,
                            leaderSerie, top64LeaderSerie, top8LeaderSerie),
                    new File(outputDir, statsFileName));

            renderToFile(new EventModel(event.name(), event, countryFlag, statsFileName, decks, decks.isEmpty(),
                            nMostResults(leaderBag, 4), nMostResults(baseBag, 4),
                            videoLinks),
                    new File(outputDir, eventFileName));
            eventPages.add(new EventPage(event, countryFlag, eventFileName));
        }

        Collections.sort(eventPages, Comparator.reverseOrder());
        renderToFile(new EventIndexModel("Events", eventPages),
                new File(outputDir, "events.html"));

        final int totalDecks = deckBag.size();
        final int totalTop8Decks = deckTop8Bag.size();
        final int totalCards = cardBag.size();
        final var topDecks = deckBag.topOccurrences(5).stream()
                .limit(5)
                .map(e -> new KeyValue(e.getOne(), (int) (e.getTwo() / (double) totalDecks * 100)))
                .sorted(Comparator.comparingInt(KeyValue::value).reversed())
                .toList();
        final var topCards = cardBag.topOccurrences(5).stream()
                .limit(5)
                .map(e -> new KeyValue(e.getOne(), (int) (e.getTwo() / (double) totalCards * 100)))
                .sorted(Comparator.comparingInt(KeyValue::value).reversed())
                .toList();
        final var top8Decks = deckTop8Bag.topOccurrences(5).stream()
                .limit(5)
                .map(e -> new KeyValue(e.getOne(), (int) (e.getTwo() / (double) totalTop8Decks * 100)))
                .sorted(Comparator.comparingInt(KeyValue::value).reversed())
                .toList();

        renderToFile(new IndexModel(null,
                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).format(lastEventDate),
                        totalDecks, topDecks, topCards, top8Decks),
                new File(outputDir, "index.html"));
    }

    private List<KeyValue> nMostResults(MutableBag<String> bag, int n) {
        MutableList<ObjectIntPair<String>> allItemsRanked = bag.topOccurrences(bag.sizeDistinct());
        MutableMap<String, Integer> result = UnifiedMap.newMap();
        int topCount = Math.min(n, allItemsRanked.size());
        for (int i = 0; i < topCount; i++) {
            ObjectIntPair<String> pair = allItemsRanked.get(i);
            result.put(pair.getOne(), pair.getTwo());
        }
        int othersTotal = 0;
        for (int i = topCount; i < allItemsRanked.size(); i++) {
            othersTotal += allItemsRanked.get(i).getTwo();
        }
        if (othersTotal > 0) {
            result.put("Others", othersTotal);
        }
        return result.entrySet().stream().map(e -> new KeyValue(e.getKey(), e.getValue())).toList();
    }

    @JStache(path = "/templates/index.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record IndexModel(String title, String lastEventDate, int totalDecks,
                      List<KeyValue> topDecks, List<KeyValue> topCards,
                      List<KeyValue> top8Decks) implements TemplateSupport {
    }

    @JStache(path = "/templates/about.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record AboutModel(String title) implements TemplateSupport {
    }

    @JStache(path = "/templates/version.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record VersionModel() implements TemplateSupport {
    }

    @JStache(path = "/templates/events.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record EventIndexModel(String title, List<EventPage> events) implements TemplateSupport {
    }

    record EventPage(
            Event event, String countryFlag, String page
    ) implements Comparable<EventPage> {
        @Override
        public int compareTo(EventPage o) {
            return event.compareTo(o.event);
        }
    }

    @JStache(path = "/templates/event.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record EventModel(String title, Event event, String countryFlag, String statsPage,
                      List<DeckWithRank> decks, boolean noDeck,
                      List<KeyValue> leaderSerie,
                      List<KeyValue> baseSerie,
                      List<Link> videoLinks) implements TemplateSupport {
    }

    record KeyValue(
            String key,
            int value
    ) {
    }

    record DeckWithRank(int rank, Deck deck, List<Card.Aspect> aspects, String swudbFormat) {
    }

    @JStache(path = "/templates/event-stats.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record EventStatsModel(String title, Event event, String countryFlag,
                           List<KeyValue> allLeaderSerie,
                           List<KeyValue> top64LeaderSerie,
                           List<KeyValue> top8LeaderSerie) implements TemplateSupport {
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

    private static void listFilesRecursively(File directory, List<File> filesList) {
        final var files = directory.listFiles();
        if (files != null) {
            for (final var file : files) {
                if (file.isFile() && file.getName().endsWith(".yaml")) {
                    filesList.add(file);
                } else if (file.isDirectory()) {
                    listFilesRecursively(file, filesList);
                }
            }
        }
    }

    private static List<Card.Aspect> getAspects(Deck d) {
        final var aspects = new ArrayList<Card.Aspect>(3);
        if (d.leader().aspects() != null) {
            aspects.addAll(d.leader().aspects());
        }
        if (d.base().aspects() != null) {
            for (final var a : d.base().aspects()) {
                aspects.add(a);
            }
        }
        Collections.sort(aspects);
        return aspects;
    }

    private static String getCountryCodeFromName(String countryName) {
        if ("USA".equals(countryName)) {
            return "us";
        }
        for (final var iso : Locale.getISOCountries()) {
            final var locale = new Locale("", iso);
            if (locale.getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(countryName)) {
                return iso.toLowerCase(Locale.ENGLISH);
            }
        }
        throw new RuntimeException("Unable to find country code: " + countryName);
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
                        case Event.Type.SHOWDOWN -> "Store Showdown";
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
                .queryParam("parent", config.domain())
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
}

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

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheFormatter;
import io.jstach.jstache.JStacheFormatterTypes;
import io.jstach.jstachio.JStachio;
import net.swumeta.cli.*;
import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.Event;
import net.swumeta.cli.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

@Component
class GenerateSiteCommand {
    private final Logger logger = LoggerFactory.getLogger(GenerateSiteCommand.class);
    private final CardDatabaseService cardDatabaseService;
    private final EventService eventService;
    private final DeckService deckService;
    private final JStachio jStachio;
    private final AppConfig config;
    private final StaticResources staticResources;

    GenerateSiteCommand(CardDatabaseService cardDatabaseService, EventService eventService, DeckService deckService, AppConfig config, StaticResources staticResources) {
        this.eventService = eventService;
        this.deckService = deckService;
        this.cardDatabaseService = cardDatabaseService;
        this.config = config;
        this.staticResources = staticResources;
        this.jStachio = JStachio.of();
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

        renderToFile(new IndexModel("Home"), new File(outputDir, "index.html"));
        renderToFile(new AboutModel("About"), new File(outputDir, "about.html"));

        final var dbDir = config.database();
        final var eventFiles = new ArrayList<File>(16);
        listFilesRecursively(new File(dbDir, "events"), eventFiles);

        final var eventPages = new ArrayList<EventPage>(eventFiles.size());
        for (final var eventFile : eventFiles) {
            final var event = eventService.load(eventFile.toURI());

            final List<DeckWithRank> decks;
            if (event.decks() == null) {
                decks = List.of();
            } else {
                decks = event.decks().stream()
                        .filter(d -> d.url() != null)
                        .map(d -> {
                            try {
                                final var deck = deckService.load(d.url());
                                if (deck.isValid()) {
                                    return new DeckWithRank(d.rank(), deck, getAspects(deck));
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
            renderToFile(new EventModel(event.name(), event, countryFlag, decks),
                    new File(outputDir, eventFileName));
            eventPages.add(new EventPage(event, countryFlag, eventFileName));
        }

        Collections.sort(eventPages, Comparator.reverseOrder());
        renderToFile(new EventIndexModel("Events", eventPages),
                new File(outputDir, "events.html"));
    }

    @JStache(path = "/templates/index.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record IndexModel(String title) implements TemplateSupport {
    }

    @JStache(path = "/templates/about.mustache")
    @JStacheConfig(formatter = CustomFormatter.class)
    record AboutModel(String title) implements TemplateSupport {
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
    record EventModel(String title, Event event, String countryFlag,
                      List<DeckWithRank> decks) implements TemplateSupport {
    }

    record DeckWithRank(int rank, Deck deck, List<Card.Aspect> aspects) {
    }

    interface TemplateSupport {
        default int year() {
            return LocalDate.now().getYear();
        }

        default LocalDateTime now() {
            return LocalDateTime.now();
        }
    }

    private void renderToFile(Object model, File output) {
        logger.debug("Generating file: {}", output.getName());
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
        aspects.addAll(d.leader().aspects());
        for (final var a : d.base().aspects()) {
            aspects.add(a);
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

    private static String md5(File file) {
        try {
            final var md = MessageDigest.getInstance("MD5");
            final var digest = md.digest(Files.readAllBytes(file.toPath()));
            return HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to calculate MD5 sum for file " + file, e);
        }
    }

    @JStacheFormatter
    @JStacheFormatterTypes(types = {Location.class, Event.Type.class, LocalDate.class, LocalDateTime.class, Card.Aspect.class, net.swumeta.cli.model.Set.class, CharSequence.class})
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
                if (o instanceof LocalDateTime dt) {
                    return dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
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
}

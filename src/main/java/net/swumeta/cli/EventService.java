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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.swumeta.cli.model.Event;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class EventService {
    private static final Predicate<Event> NULL_FILTER = e -> true;
    private final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final DeckService deckService;
    private final AppConfig config;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    EventService(DeckService deckService, AppConfig config, RestClient client) {
        this.deckService = deckService;
        this.config = config;
        this.client = client;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.findAndRegisterModules();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ImmutableList<Event> list() {
        return list(null);
    }

    public ImmutableList<Event> list(Predicate<Event> filter) {
        final var f = filter == null ? NULL_FILTER : filter;
        final var events = Lists.immutable.fromStream(getEventFiles().stream().map(this::load).filter(f));
        if (logger.isTraceEnabled()) {
            logger.trace("Found events: {}", events.stream().map(Event::name).toList());
        }
        return events.toImmutable();
    }

    private ImmutableList<File> getEventFiles() {
        final var eventsDir = new File(config.database(), "events");
        logger.trace("Listing event files in directory: {}", eventsDir);
        final var eventFiles = new ArrayList<File>(32);
        listFilesRecursively(eventsDir, eventFiles);
        return Lists.immutable.ofAll(eventFiles);
    }

    public Event sync(Event event) {
        Assert.notNull(event, "Event must not be null");
        if (event.locked()) {
            logger.info("Skipping event since it's locked: {}", event);
            return event;
        }
        if (event.melee() == null) {
            logger.warn("Event '{}' has no Melee.gg link", event);
            return event;
        }

        logger.info("Connecting to melee.gg: {}", event.melee());
        final var meleePage = client.get().uri(event.melee()).retrieve().body(String.class);
        final var meleeDoc = Jsoup.parse(meleePage);
        final var deckUris = new ArrayList<Event.DeckEntry>(32);

        int players = 0;
        final var tournamentHeadlineRegElem = meleeDoc.getElementById("tournament-headline-registration");
        if (!tournamentHeadlineRegElem.text().isEmpty()) {
            final var pattern = Pattern.compile("(\\d+)\\s+of\\s+\\d+\\s+Enrolled\\s+Players");
            final var matcher = pattern.matcher(tournamentHeadlineRegElem.text());
            if (matcher.find()) {
                players = Integer.parseInt(matcher.group(1));
            }
        }

        final var standingsElem = meleeDoc.getElementById("standings-round-selector-container");
        if (standingsElem != null) {
            final var roundStandingsElems = standingsElem.getElementsByAttributeValue("data-is-completed", "True");
            if (roundStandingsElems != null && !roundStandingsElems.isEmpty()) {
                final var lastRoundElem = roundStandingsElems.last();
                final var roundId = Integer.parseInt(lastRoundElem.attr("data-id"));
                logger.trace("Found round id: {}", roundId);

                final int pageSize = 25;
                int rank = 1;
                for (int page = 0; ; page += 1) {
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
                    body.add("start", String.valueOf(page * pageSize));
                    body.add("length", String.valueOf(pageSize));
                    body.add("search[value]", "");
                    body.add("search[regex]", "false");
                    body.add("roundId", String.valueOf(roundId));

                    final var resp = client.post()
                            .uri("https://melee.gg/Standing/GetRoundStandings")
                            .accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(body)
                            .retrieve().body(JsonRoot.class);

                    if (resp.recordsTotal == 0 || resp.data.isEmpty()) {
                        break;
                    }

                    for (final var player : resp.data) {
                        if ("0-0-0".equals(player.MatchRecord)) {
                            logger.warn("Skipping decklist at rank {} for round {} since match record is 0-0-0", player.Rank, roundId);
                            continue;
                        }
                        URI deckUri = null;
                        if (player.Decklists.isEmpty()) {
                            logger.warn("Missing decklist at rank {} for round {}", player.Rank, roundId);
                        } else {
                            final var deckId = player.Decklists.get(0).DecklistId;
                            deckUri = UriComponentsBuilder.fromUriString("https://melee.gg/Decklist/View/").path(deckId).build().toUri();
                        }
                        logger.trace("Adding deck URI at rank {}: {}", player.Rank, deckUri);
                        deckUris.add(new Event.DeckEntry(rank++, false, deckUri, null, null, null));
                    }
                }
            }
        }

        final var newEvent = new Event(
                event.name(), false, event.type(), players == 0 ? event.players() : players, event.date(), event.location(), event.hidden(), event.format(),
                event.melee(), event.contributors(), event.links(), deckUris
        );

        if (newEvent.equals(event)) {
            logger.debug("Event is already up-to-date: {}", newEvent);
            return event;
        }

        logger.info("Deleting cache for event: {}", newEvent);
        deckService.delete(event.decks().stream().map(Event.DeckEntry::url).toList());

        File eventFile = null;
        for (final var fileCandidate : getEventFiles()) {
            final var eventCandidate = load(fileCandidate);
            if (eventCandidate.melee() != null && eventCandidate.melee().equals(event.melee())) {
                eventFile = fileCandidate;
                break;
            }
        }
        if (eventFile == null) {
            throw new AppException("Unable to find file for event: " + event);
        }
        try {
            logger.info("Saving event '{}' to file: {}", event, eventFile);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(eventFile, newEvent);
        } catch (IOException e) {
            throw new AppException("Failed to save event to file: " + eventFile, e);
        }

        return newEvent;
    }

    private Event load(File file) {
        try {
            return objectMapper.readerFor(Event.class).readValue(file);
        } catch (IOException e) {
            throw new AppException("Failed to load event from file: " + file, e);
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

    private record JsonRoot(
            List<JsonPlayer> data,
            int recordsTotal
    ) {
    }

    private record JsonPlayer(
            int Rank,
            List<JsonPlayerDeck> Decklists,
            String MatchRecord
    ) {
    }

    private record JsonPlayerDeck(
            String DecklistId
    ) {
    }
}

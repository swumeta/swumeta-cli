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

import net.swumeta.cli.DeckService;
import net.swumeta.cli.model.Event;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
class GetMeleeDecksCommand {
    private final Logger logger = LoggerFactory.getLogger(GetMeleeDecksCommand.class);
    private final DeckService deckService;
    private final RestClient client;

    GetMeleeDecksCommand(DeckService deckService, RestClient client) {
        this.deckService = deckService;
        this.client = client;
    }

    void run(URI uri, int max, int roundIdOpt) {
        logger.info("Connecting to melee.gg: {}", uri);
        final var meleePage = client.get().uri(uri).retrieve().body(String.class);
        final var meleeDoc = Jsoup.parse(meleePage);
        final var standingsElem = meleeDoc.getElementById("standings-round-selector-container");
        final var roundStandingsElems = standingsElem.getElementsByAttributeValue("data-is-completed", "True");
        if (roundStandingsElems.isEmpty()) {
            logger.warn("Found no completed round");
            return;
        }

        final var lastRoundElem = roundStandingsElems.last();
        final var roundId = roundIdOpt == 0 ? Integer.parseInt(lastRoundElem.attr("data-id")) : roundIdOpt;
        logger.debug("Found round id: {}", roundId);

        final var deckUris = new ArrayList<Event.DeckEntry>(32);

        while (true) {
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
            body.add("start", String.valueOf(deckUris.size()));
            body.add("length", "25");
            body.add("search[value]", "");
            body.add("search[regex]", "false");
            body.add("roundId", String.valueOf(roundId));

            final var resp = client.post()
                    .uri("https://melee.gg/Standing/GetRoundStandings")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve().body(JsonRoot.class);

            for (final var player : resp.data) {
                URI deckUri = null;
                if (player.Decklists.isEmpty()) {
                    logger.warn("Missing decklist at rank {} for round {}", player.Rank, roundId);
                } else {
                    final var deckId = player.Decklists.get(0).DecklistId;
                    deckUri = UriComponentsBuilder.fromUriString("https://melee.gg/Decklist/View/").path(deckId).build().toUri();
                }
                logger.debug("Adding deck URI at rank {}: {}", player.Rank, deckUri);
                deckUris.add(new Event.DeckEntry(player.Rank, true, deckUri));

                if (deckUris.size() >= resp.recordsTotal || (max != 0 && deckUris.size() >= max)) {
                    break;
                }
            }
            if (deckUris.size() >= resp.recordsTotal || (max != 0 && deckUris.size() >= max)) {
                break;
            }
        }

        if (deckUris.isEmpty()) {
            logger.info("Found no decks");
            return;
        }

        final var buf = new StringBuilder(1024);
        for (final var deckUri : deckUris) {
            buf.append("\n").append("- rank: ").append(deckUri.rank()).append("\n  url: ").append(deckUri.url());
        }

        logger.info("Decks:{}", buf);
    }

    private record JsonRoot(
            List<JsonPlayer> data,
            int recordsTotal
    ) {
    }

    private record JsonPlayer(
            int Rank,
            List<JsonPlayerDeck> Decklists
    ) {
    }

    private record JsonPlayerDeck(
            String DecklistId
    ) {
    }
}

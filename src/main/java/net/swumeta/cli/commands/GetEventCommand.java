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
import net.swumeta.cli.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.format.DateTimeFormatter;

@Component
class GetEventCommand {
    private final Logger logger = LoggerFactory.getLogger(GetEventCommand.class);
    private final EventService eventService;
    private final DeckService deckService;

    GetEventCommand(EventService eventService, DeckService deckService) {
        this.eventService = eventService;
        this.deckService = deckService;
    }

    void run(File file) {
        final var event = eventService.load(file.toURI());
        final var buf = new StringBuffer(256);
        buf.append("Name: ").append(event.name()).append("\n")
                .append("Type: ").append(event.type()).append("\n")
                .append("Date: ").append(DateTimeFormatter.ISO_LOCAL_DATE.format(event.date())).append("\n");
        if (event.location() != null) {
            buf.append("Location: ");
            if (event.location().city() != null) {
                buf.append(event.location().city()).append(", ");
            }
            buf.append(event.location().country()).append("\n");
        }
        if (event.melee() != null) {
            buf.append("Melee.gg: ").append(event.melee()).append("\n");
        }
        if (event.players() != 0) {
            buf.append("Players: ").append(event.players()).append("\n");
        }
        if (!event.decks().isEmpty()) {
            logger.info("Loading decks details...");
            buf.append("Top:");
            event.decks().stream()
                    .limit(8)
                    .forEach(deckUri -> {
                        buf.append("\n").append("%3d".formatted(deckUri.rank())).append(". ");
                        if (deckUri.url() == null) {
                            buf.append("N/A");
                        } else {
                            final var deck = deckService.load(deckUri.url());
                            buf.append(deckService.formatName(deck)).append(" | ").append(deck.source());
                        }
                    });
        }
        logger.info(buf.toString());
    }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
class GetDeckCommand {
    private final Logger logger = LoggerFactory.getLogger(GetDeckCommand.class);
    private final DeckService deckService;

    GetDeckCommand(DeckService deckService) {
        this.deckService = deckService;
    }

    void run(URI uri) {
        final var deck = deckService.load(uri);
        logger.info("Name: {}", new DeckFormatter().format(deck));
    }
}

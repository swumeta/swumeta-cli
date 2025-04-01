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

import net.swumeta.cli.CardDatabaseService;
import net.swumeta.cli.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.TreeSet;

@Component
class FindCardsCommand {
    private final Logger logger = LoggerFactory.getLogger(FindCardsCommand.class);
    private final CardDatabaseService cardDatabaseService;

    FindCardsCommand(CardDatabaseService cardDatabaseService) {
        this.cardDatabaseService = cardDatabaseService;
    }

    void run(String title, String subtitle) {
        final var ret = cardDatabaseService.findByName(title, subtitle);
        if (ret.isEmpty()) {
            logger.info("No card found");
        }
        final var sortedCards = new TreeSet<Card>(ret);
        for (final Card card : sortedCards) {
            logger.info("Found card: {}", card.id());
        }
    }
}

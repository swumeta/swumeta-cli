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
import net.swumeta.cli.CardDownloaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class DownloadCardsCommand {
    private final Logger logger = LoggerFactory.getLogger(DownloadCardsCommand.class);
    private final CardDownloaderService cardsDownloaderService;
    private final CardDatabaseService cardDatabaseService;

    DownloadCardsCommand(CardDownloaderService cardsDownloaderService, CardDatabaseService cardDatabaseService) {
        this.cardsDownloaderService = cardsDownloaderService;
        this.cardDatabaseService = cardDatabaseService;
    }

    void run() {
        logger.info("Downloading cards...");
        final var status = new Status();
        cardDatabaseService.clear();
        cardsDownloaderService.downloadCards(card -> {
            logger.debug("Downloaded card: {}", card);
            status.cardCount += 1;
            cardDatabaseService.save(card);
        });
        logger.info("Downloaded {} cards", status.cardCount);
    }

    private class Status {
        int cardCount;
    }
}

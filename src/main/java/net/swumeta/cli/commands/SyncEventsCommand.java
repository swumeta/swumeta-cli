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

import net.swumeta.cli.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class SyncEventsCommand {
    private final Logger logger = LoggerFactory.getLogger(SyncEventsCommand.class);
    private final EventService eventService;

    SyncEventsCommand(EventService eventService) {
        this.eventService = eventService;
    }

    void run() {
        logger.info("Synchronizing events");
        for (final var event : eventService.list()) {
            logger.info("Processing event: {}", event);
            eventService.sync(event);
        }
    }
}

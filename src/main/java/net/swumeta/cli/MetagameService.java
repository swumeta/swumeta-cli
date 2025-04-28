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

import net.swumeta.cli.model.Event;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class MetagameService {
    private final Logger logger = LoggerFactory.getLogger(MetagameService.class);
    private final EventService eventService;
    private final AppConfig config;

    public MetagameService(EventService eventService, AppConfig config) {
        this.eventService = eventService;
        this.config = config;
    }

    public record Metagame(LocalDate date, ImmutableList<Event> events) {
    }

    public Metagame getMetagame() {
        logger.debug("Listing events for the metagame");
        final Predicate<Event> eventFilter = config.metagameMonths() < 1 ? null : new EventFilter(config.metagameMonths(), config.metagameLimit());

        final var events = eventService.list(eventFilter);
        if (events.isEmpty()) {
            throw new AppException("No events found");
        }

        logger.debug("Number of events part of the metagame: {}", events.size());
        if (logger.isTraceEnabled()) {
            final var df = DateTimeFormatter.ISO_LOCAL_DATE;
            final var eventNames = events.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            logger.trace("Events part for the metagame: {}", eventNames);
        }

        LocalDate lastDate = null;
        for (final var e : events) {
            if (!e.decks().isEmpty() && (lastDate == null || e.date().isAfter(lastDate))) {
                lastDate = e.date();
            }
        }

        return new Metagame(lastDate, events);
    }

    private static class EventFilter implements Predicate<Event> {
        private final LocalDate limitDate;

        EventFilter(final int months, final LocalDate hardLimit) {
            final var dl = LocalDate.now().minusMonths(months);
            if (hardLimit != null && dl.isBefore(hardLimit)) {
                this.limitDate = hardLimit;
            } else {
                this.limitDate = dl;
            }
        }

        @Override
        public boolean test(Event event) {
            return !event.hidden() && (event.date().isAfter(limitDate) || event.date().isEqual(limitDate));
        }
    }
}

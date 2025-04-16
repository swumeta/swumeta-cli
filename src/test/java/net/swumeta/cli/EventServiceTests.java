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
import net.swumeta.cli.model.Link;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class EventServiceTests {
    @Autowired
    private EventService svc;
    @Autowired
    private AppConfig config;

    @Test
    void testLoad() {
        final var eventFile = new File(new File(config.database(), "events"), "event1.yaml");
        assertThat(eventFile).exists();
        final var event = svc.load(eventFile.toURI());
        assertThat(event).isNotNull();
        assertThat(event.links()).hasSize(2);
        assertThat(event.links()).containsExactly(new Link(URI.create("https://twitch.tv/videos/2419268107"), "Stream"), new Link(URI.create("https://www.youtube.com/watch?v=-RcTpbxRPqo"), "Review"));
        assertThat(event.decks()).hasSize(2);
        assertThat(event.decks()).containsExactly(new Event.DeckEntry(1, false, URI.create("https://melee.gg/Decklist/View/739710d1-772f-4b10-bde6-b2ae0105815a")), new Event.DeckEntry(3, false, URI.create("https://melee.gg/Decklist/View/69c0f08b-1088-48f3-adf8-b2ae00065c6d")));
    }

    @Test
    void testLoadNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> svc.load(null));
    }

    @Test
    void testLoadUnknown() {
        assertThatExceptionOfType(AppException.class).isThrownBy(() -> svc.load(new File("foo.txt").toURI()));
    }

    @Test
    void testListNoFilter() {
        final var events = new ArrayList<Event>(svc.list(null));
        assertThat(events).hasSize(2);

        Collections.sort(events, Comparator.comparing(Event::date));
        assertThat(events.get(0).name()).isEqualTo("Sector Qualifier Milan");
        assertThat(events.get(1).name()).isEqualTo("Santa Geek Café Store Showdown");
    }

    @Test
    void testListReadOnly() {
        final var events = svc.list(null);
        assertThat(events).isNotEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> svc.list(null).clear());
    }

    @Test
    void testListDateFilter() {
        final var events = svc.list(e -> e.date().isAfter(LocalDate.of(2025, 4, 10)));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).name()).isEqualTo("Santa Geek Café Store Showdown");
    }
}

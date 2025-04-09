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

import net.swumeta.cli.model.DeckLink;
import net.swumeta.cli.model.Link;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
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
        assertThat(event.links()).containsExactly(
                new Link(URI.create("https://twitch.tv/videos/2419268107"), "Stream"),
                new Link(URI.create("https://www.youtube.com/watch?v=-RcTpbxRPqo"), "Review")
        );
        assertThat(event.decks()).hasSize(2);
        assertThat(event.decks()).containsExactly(
                new DeckLink(1, false, URI.create("https://melee.gg/Decklist/View/739710d1-772f-4b10-bde6-b2ae0105815a")),
                new DeckLink(3, false, URI.create("https://melee.gg/Decklist/View/69c0f08b-1088-48f3-adf8-b2ae00065c6d"))
        );
    }

    @Test
    void testLoadNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> svc.load(null));
    }

    @Test
    void testLoadUnknown() {
        assertThatExceptionOfType(AppException.class).isThrownBy(() -> svc.load(new File("foo.txt").toURI()));
    }
}

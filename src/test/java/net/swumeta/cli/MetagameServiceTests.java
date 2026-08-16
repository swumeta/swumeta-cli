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
import net.swumeta.cli.model.Format;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MetagameServiceTests {
    @Autowired
    private MetagameService svc;
    @Autowired
    private TestHelper helper;

    @Test
    void testIsPremierEvent() {
        assertThat(svc.isPremierEvent(createEvent(Format.PREMIER))).isTrue();
        assertThat(svc.isPremierEvent(createEvent(Format.ETERNAL))).isFalse();
        assertThat(svc.isPremierEvent(createEvent(Format.TWIN_SUNS))).isFalse();
    }

    /**
     * Most events declare no format at all: those are Premier events.
     */
    @Test
    void testIsPremierEventWithoutFormat() {
        assertThat(svc.isPremierEvent(createEvent(null))).isTrue();
    }

    private Event createEvent(Format format) {
        return helper.createEvent("Event", LocalDate.of(2025, 4, 13), format, List.of());
    }

    //@Test
    void testGetMetagame() {
        final var metagame = svc.getMetagame();
        assertThat(metagame).isNotNull();
        assertThat(metagame.date()).isEqualTo(LocalDate.of(2025, 4, 13));
        assertThat(metagame.events()).hasSize(2);
        assertThat(metagame.events().collect(Event::name)).contains("Sector Qualifier Milan", "Santa Geek Café Store Showdown");
    }
}

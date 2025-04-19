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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class EventServiceTests {
    @Autowired
    private EventService svc;
    @Autowired
    private AppConfig config;

    @Test
    void testListNoFilter() {
        final var events = svc.list();
        assertThat(events).hasSize(2);
        final var eventNames = events.valuesView().collect(e -> e.name());
        assertThat(eventNames).contains("Sector Qualifier Milan", "Santa Geek Café Store Showdown");
    }

    @Test
    void testListDateFilter() {
        final var events = svc.list(e -> e.date().isAfter(LocalDate.of(2025, 4, 10)));
        assertThat(events).hasSize(1);
        assertThat(events.valuesView().getAny().name()).isEqualTo("Santa Geek Café Store Showdown");
    }
}

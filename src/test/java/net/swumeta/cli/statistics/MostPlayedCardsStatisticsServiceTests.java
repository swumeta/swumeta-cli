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

package net.swumeta.cli.statistics;

import net.swumeta.cli.TestHelper;
import org.eclipse.collections.api.factory.Bags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MostPlayedCardsStatisticsServiceTests {
    @Autowired
    private MostPlayedCardsStatisticsService svc;
    @Autowired
    private TestHelper helper;

    @Test
    void testGetMostPlayedCardsStatistics() {
        final var deck1 = helper.createDeck("JTL-009", "JTL-026",
                Bags.immutable.ofOccurrences("JTL-143", 2),
                Bags.immutable.ofOccurrences("JTL-045", 3)
        );
        final var deck2 = helper.createDeck("SOR-008", "SOR-028",
                Bags.immutable.ofOccurrences("JTL-143", 3),
                Bags.immutable.ofOccurrences("JTL-045", 1)
        );
        final var event = helper.createEvent("Foo", LocalDate.now(), List.of(deck1, deck2));
        final var stats = svc.getMostPlayedCardsStatistics(List.of(event));
        assertThat(stats).isNotNull();
        assertThat(stats.cards().sizeDistinct()).isEqualTo(2);
        assertThat(stats.cards().occurrencesOf("JTL-143")).isEqualTo(5);
        assertThat(stats.cards().occurrencesOf("JTL-045")).isEqualTo(4);
    }
}

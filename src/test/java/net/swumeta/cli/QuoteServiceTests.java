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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuoteServiceTests {
    @Autowired
    private QuoteService svc;

    @Test
    void testRandomQuote() {
        final var quote1 = getRandomQuote();
        final var quote2 = getRandomQuote();
        assertThat(quote1).isNotNull();
        assertThat(quote2).isNotNull();
        assertThat(quote1).isNotEqualTo(quote2);
    }

    private String getRandomQuote() {
        // This ensures more "randomness".
        for(int i = 0; i< 10; ++i) {
            svc.randomQuote();
        }
        return svc.randomQuote();
    }
}

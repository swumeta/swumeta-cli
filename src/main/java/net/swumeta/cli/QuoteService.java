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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class QuoteService {
    private final Logger logger = LoggerFactory.getLogger(QuoteService.class);
    private final Random random = new Random();
    private final List<String> quotes = new ArrayList<>(96);

    public String randomQuote() {
        if (quotes.isEmpty()) {
            loadQuotes();
        }
        final var i = random.nextInt(quotes.size());
        return quotes.get(i);
    }

    private void loadQuotes() {
        logger.debug("Loading quotes");
        final var res = new ClassPathResource("quotes.txt");
        try (final var reader = new BufferedReader(new InputStreamReader(res.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; ) {
                final var trimmed = line.trim();
                if (trimmed.length() > 0 && !trimmed.startsWith("#")) {
                    quotes.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load quotes", e);
        }
    }
}

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

package net.swumeta.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.springframework.util.DigestUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Deck(
        @JsonProperty(required = true) URI source,
        @JsonProperty(required = true) String author,
        @JsonProperty(required = true) Format format,
        @JsonProperty(required = true) Card leader,
        @JsonProperty(required = true) Card base,
        @JsonProperty(required = true) List<Card> main,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Card> sideboard
) {
    public String name() {
        return new StringBuffer(64).append(formatLeader()).append(" - ").append(formatBase()).toString();
    }

    public String id() {
        return DigestUtils.md5DigestAsHex(source.toASCIIString().getBytes(StandardCharsets.UTF_8));
    }

    public boolean isValid() {
        return leader != null && base != null && main != null && !main.isEmpty();
    }

    public String formatLeader() {
        return new StringBuffer(32).append(leader.name()).append(" (").append(leader.set()).append(")").toString();
    }

    public String formatBase() {
        if (base.rarity().equals(Card.Rarity.COMMON)) {
            if (base.aspects().isEmpty()) {
                return base.name();
            }
            return switch (base.aspects().get(0)) {
                case VIGILANCE -> "Blue";
                case COMMAND -> "Green";
                case AGGRESSION -> "Red";
                case CUNNING -> "Yellow";
                default -> base.name();
            };
        }
        return base.name();
    }

    public String toSwudbJson(ObjectMapper objectMapper) {
        final var swudbDeck = new ArrayList<SwudbCard>(50);
        if (main != null) {
            final MutableBag<Card> bag = HashBag.newBag(30);
            bag.addAll(main);
            bag.forEachWithOccurrences((ObjectIntProcedure<Card>) (card, count) -> {
                swudbDeck.add(toSwudbCard(card, count));
            });
        }
        final var swudbSideboard = new ArrayList<SwudbCard>(10);
        if (sideboard != null) {
            final MutableBag<Card> bag = HashBag.newBag(5);
            bag.addAll(sideboard);
            bag.forEachWithOccurrences((ObjectIntProcedure<Card>) (card, count) -> {
                swudbSideboard.add(toSwudbCard(card, count));
            });
        }
        final var d = new SwudbDeck(
                new SwudbMetadata(name(), author),
                toSwudbCard(leader, 1),
                toSwudbCard(base, 1),
                swudbDeck,
                swudbSideboard
        );
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert deck to swudb format", e);
        }
    }

    private SwudbCard toSwudbCard(Card c, int count) {
        return new SwudbCard(c.id().replace("-", "_"), count);
    }

    private record SwudbDeck(
            SwudbMetadata metadata,
            SwudbCard leader,
            SwudbCard base,
            List<SwudbCard> deck,
            List<SwudbCard> sideboard
    ) {
    }

    private record SwudbMetadata(
            String name,
            String author
    ) {
    }

    private record SwudbCard(
            String id,
            int count
    ) {
    }
}

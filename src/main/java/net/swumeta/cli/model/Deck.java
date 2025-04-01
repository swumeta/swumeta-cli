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

import java.net.URI;
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
    public CharSequence name() {
        return new StringBuffer(64).append(formatLeader(leader)).append(" - ").append(formatBase(base));
    }

    public boolean isValid() {
        return leader != null && base != null && main != null && !main.isEmpty();
    }

    private static CharSequence formatLeader(Card card) {
        return new StringBuffer(32).append(card.name()).append(" (").append(card.set()).append(")");
    }

    private static String formatBase(Card card) {
        if (card.rarity().equals(Card.Rarity.COMMON)) {
            if (card.aspects().isEmpty()) {
                return card.name();
            }
            return switch (card.aspects().get(0)) {
                case VIGILANCE -> "Blue";
                case COMMAND -> "Green";
                case AGGRESSION -> "Red";
                case CUNNING -> "Yellow";
                default -> card.name();
            };
        }
        return card.name();
    }
}

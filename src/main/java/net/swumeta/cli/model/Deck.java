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
        return new StringBuffer(64).append(formatLeader()).append(" - ").append(formatBase());
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
}

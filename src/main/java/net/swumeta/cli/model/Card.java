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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Card(
        @JsonProperty(required = true) Set set,
        @JsonProperty(required = true) int number,
        @JsonProperty(required = true) Type type,
        @JsonProperty(required = true) Rarity rarity,
        Arena arena,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Aspect> aspects,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) int cost,
        @JsonProperty(required = true) String name,
        String title,
        @JsonProperty(required = true) URI art,
        @JsonProperty(required = true) URI thumbnail
) implements Comparable<Card> {
    public enum Type {
        @JsonProperty("leader") LEADER,
        @JsonProperty("base") BASE,
        @JsonProperty("unit") UNIT,
        @JsonProperty("event") EVENT,
        @JsonProperty("upgrade") UPGRADE
    }

    public enum Arena {
        @JsonProperty("ground") GROUND,
        @JsonProperty("space") SPACE
    }

    public enum Rarity {
        @JsonProperty("special") SPECIAL,
        @JsonProperty("common") COMMON,
        @JsonProperty("uncommon") UNCOMMON,
        @JsonProperty("rare") RARE,
        @JsonProperty("legendary") LEGENDARY
    }

    public enum Aspect {
        @JsonProperty("villainy") VILLAINY,
        @JsonProperty("heroism") HEROISM,
        @JsonProperty("vigilance") VIGILANCE,
        @JsonProperty("command") COMMAND,
        @JsonProperty("aggression") AGGRESSION,
        @JsonProperty("cunning") CUNNING,
    }

    @Override
    public int compareTo(Card o) {
        if (!set.equals(o.set)) {
            return set.compareTo(o.set);
        }
        if (number == o.number) {
            return 0;
        }
        return number < o.number ? -1 : 1;
    }

    public Card.Id id() {
        return new Card.Id(set, number);
    }

    public static final class Id {
        private static final LoadingCache<String, Id> CACHE = Caffeine.newBuilder().weakKeys().weakValues().build(Id::new);
        private final Set set;
        private final int number;

        Id(Set set, int number) {
            this.set = set;
            this.number = number;
        }

        @JsonCreator
        Id(String value) {
            final int i = value.indexOf('-');
            final var setStr = value.substring(0, i);
            this.set = Set.valueOf(setStr);

            final var numberStr = value.substring(i + 1);
            this.number = Integer.parseInt(numberStr);
        }

        public Set set() {
            return set;
        }

        public int number() {
            return number;
        }

        public static Card.Id valueOf(String value) {
            return CACHE.get(value);
        }

        @Override
        @JsonValue
        public String toString() {
            return "%s-%03d".formatted(set.name(), number);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Id id)) return false;
            return number == id.number && set == id.set;
        }

        @Override
        public int hashCode() {
            return Objects.hash(set, number);
        }
    }
}

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

    public String id() {
        return "%s-%03d".formatted(set.name(), number);
    }
}

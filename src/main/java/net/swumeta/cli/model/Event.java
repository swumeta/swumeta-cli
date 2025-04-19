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
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Event(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) Type type,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) int players,
        @JsonProperty(required = true) LocalDate date,
        Location location,
        @JsonProperty(defaultValue = "false") boolean hidden,
        @JsonProperty(defaultValue = "premier") Format format,
        URI melee,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<String> contributors,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<Link> links,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<DeckEntry> decks
) implements Comparable<Event> {
    @Override
    public int compareTo(Event o) {
        if (!date.equals(o.date)) {
            return date.compareTo(o.date);
        }
        return name.compareTo(o.name);
    }

    public enum Type {
        @JsonProperty("major") MAJOR,
        @JsonProperty("planetary-qualifier") PQ,
        @JsonProperty("sector-qualifier") SQ,
        @JsonProperty("regional-qualifier") RQ,
        @JsonProperty("galactic-championship") GS
    }

    public record DeckEntry(
            @JsonProperty(required = true) int rank,
            @JsonProperty(defaultValue = "false") boolean pending,
            URI url
    ) {
    }

    @Override
    public String toString() {
        return "%s (%s)".formatted(name, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }
}

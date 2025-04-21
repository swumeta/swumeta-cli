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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.factory.Bags;
import org.springframework.util.DigestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Deck(
        @JsonProperty(required = true) URI source,
        @JsonProperty(required = true) String author,
        @JsonProperty(required = true) Format format,
        @JsonProperty(required = true) Card.Id leader,
        @JsonProperty(required = true) Card.Id base,
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonSerialize(using = CardEntrySerializer.class) @JsonDeserialize(using = CardEntryDeserializer.class) ImmutableBag<Card.Id> main,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonSetter(nulls = Nulls.AS_EMPTY) @JsonSerialize(using = CardEntrySerializer.class) @JsonDeserialize(using = CardEntryDeserializer.class) ImmutableBag<Card.Id> sideboard
) {
    public String id() {
        return DigestUtils.md5DigestAsHex(
                UriComponentsBuilder.fromUri(source).port(80).toUriString().getBytes(StandardCharsets.UTF_8));
    }

    @JsonIgnore
    public boolean isValid() {
        return leader != null && base != null;
    }

    private record CardEntry(
            @JsonProperty(required = true) Card.Id card,
            @JsonProperty(required = true) int count
    ) implements Comparable<CardEntry> {
        @Override
        public int compareTo(CardEntry o) {
            if (card.compareTo(o.card) != 0) {
                return card.compareTo(o.card);
            }
            if (count == o.count) {
                return 0;
            }
            return count < o.count ? -1 : 1;
        }
    }

    private static class CardEntrySerializer extends StdSerializer<ImmutableBag> {
        public CardEntrySerializer() {
            super(ImmutableBag.class);
        }

        @Override
        public void serialize(ImmutableBag value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            var bag = (ImmutableBag<Card.Id>) value;
            if (bag == null) {
                bag = Bags.immutable.empty();
            }
            final var entries = new ArrayList<CardEntry>(bag.size());
            bag.forEachWithOccurrences((ObjectIntProcedure<Card.Id>) (card, count) -> entries.add(new CardEntry(card, count)));
            Collections.sort(entries);
            gen.writeObject(entries);
        }
    }

    private static class CardEntryDeserializer extends StdDeserializer<ImmutableBag> {
        public CardEntryDeserializer() {
            super(ImmutableBag.class);
        }

        @Override
        public ImmutableBag deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            final var entries = p.readValueAs(CardEntry[].class);
            if (entries == null || entries.length == 0) {
                return Bags.immutable.empty();
            }
            Arrays.sort(entries);
            final var bag = Bags.mutable.<Card.Id>withInitialCapacity(entries.length);
            for (final var e : entries) {
                bag.addOccurrences(e.card, e.count);
            }
            return bag.toImmutableBag();
        }
    }
}

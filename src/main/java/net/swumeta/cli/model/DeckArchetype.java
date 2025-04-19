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

import org.springframework.lang.Nullable;

import java.util.Objects;

public final class DeckArchetype {
    private final Card.Id leader;
    private final @Nullable Card.Aspect aspect;
    private final @Nullable Card.Id base;

    DeckArchetype(Card.Id leader, @Nullable Card.Aspect aspect, @Nullable Card.Id base) {
        this.leader = leader;
        this.aspect = aspect;
        this.base = base;
    }

    public Card.Id leader() {
        return leader;
    }

    @Nullable
    public Card.Aspect aspect() {
        return aspect;
    }

    @Nullable
    public Card.Id base() {
        return base;
    }

    public static DeckArchetype valueOf(Card.Id leader, Card.Aspect aspect) {
        return new DeckArchetype(leader, aspect, null);
    }

    public static DeckArchetype valueOf(Card.Id leader, Card.Id base) {
        return new DeckArchetype(leader, null, base);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DeckArchetype that)) return false;
        return Objects.equals(leader, that.leader) && aspect == that.aspect && Objects.equals(base, that.base);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leader, aspect, base);
    }
}

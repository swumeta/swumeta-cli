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

package net.swumeta.cli.commands;

import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Deck;

import java.util.Comparator;

class DeckFormatter {
    public CharSequence format(Deck deck) {
        final var buf = new StringBuffer(256);
        // TODO implement Twin Suns
        buf.append(deck.name()).append("\n").append("Main:");
        deck.main().stream().filter(c -> Card.Arena.GROUND.equals(c.arena())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
            final var quantity = deck.main().stream().filter(c2 -> c2.id().equals(c.id())).count();
            buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
        });
        deck.main().stream().filter(c -> Card.Arena.SPACE.equals(c.arena())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
            final var quantity = deck.main().stream().filter(c2 -> c2.id().equals(c.id())).count();
            buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
        });
        deck.main().stream().filter(c -> Card.Type.EVENT.equals(c.type())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
            final var quantity = deck.main().stream().filter(c2 -> c2.id().equals(c.id())).count();
            buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
        });
        deck.main().stream().filter(c -> Card.Type.UPGRADE.equals(c.type())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
            final var quantity = deck.main().stream().filter(c2 -> c2.id().equals(c.id())).count();
            buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
        });

        if (!deck.sideboard().isEmpty()) {
            buf.append("\nSideboard:");
            deck.sideboard().stream().filter(c -> Card.Arena.GROUND.equals(c.arena())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
                final var quantity = deck.sideboard().stream().filter(c2 -> c2.id().equals(c.id())).count();
                buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
            });
            deck.sideboard().stream().filter(c -> Card.Arena.SPACE.equals(c.arena())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
                final var quantity = deck.sideboard().stream().filter(c2 -> c2.id().equals(c.id())).count();
                buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
            });
            deck.sideboard().stream().filter(c -> Card.Type.EVENT.equals(c.type())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
                final var quantity = deck.sideboard().stream().filter(c2 -> c2.id().equals(c.id())).count();
                buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
            });
            deck.sideboard().stream().filter(c -> Card.Type.UPGRADE.equals(c.type())).sorted(Comparator.comparingInt(Card::cost)).distinct().forEach(c -> {
                final var quantity = deck.sideboard().stream().filter(c2 -> c2.id().equals(c.id())).count();
                buf.append("\n").append(quantity).append(" | ").append(formatCard(c));
            });
        }
        return buf;
    }

    private static CharSequence formatCard(Card c) {
        final var buf = new StringBuffer(64).append(c.name());
        if (c.title() != null) {
            buf.append(" | ").append(c.title());
        }
        return buf;
    }
}

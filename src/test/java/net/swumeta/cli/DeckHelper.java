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

import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.Format;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class DeckHelper {
    public Deck createDeck(String leader, String base) {
        return new Deck(URI.create("http://foo.bar"), "Me", Format.PREMIER, leader, base, Bags.immutable.empty(), Bags.immutable.empty());
    }

    public Deck createDeck(String leader, String base, ImmutableBag<String> main, ImmutableBag<String> sideboard) {
        return new Deck(URI.create("http://foo.bar"), "Me", Format.PREMIER, leader, base, main, sideboard);
    }
}

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.swumeta.cli.model.Deck;
import net.swumeta.cli.model.Format;
import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.factory.Bags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
class DeckServiceTests {
    @Autowired
    private DeckService svc;
    @Value("${wiremock.server.port}")
    private int wiremockPort;

    @Test
    void testFormatName() {
        assertThat(svc.formatName(createDeck("JTL-009", "JTL-026"))).isEqualTo("Boba Fett (JTL) - Red");
        assertThat(svc.formatName(createDeck("JTL-009", "JTL-021"))).isEqualTo("Boba Fett (JTL) - Colossus");
    }

    @Test
    void testFormatLeader() {
        assertThat(svc.formatLeader(createDeck("JTL-009", "JTL-026"))).isEqualTo("Boba Fett (JTL)");
    }

    @Test
    void testFormatBase() {
        assertThat(svc.formatBase(createDeck("JTL-009", "JTL-026"))).isEqualTo("Red");
        assertThat(svc.formatBase(createDeck("JTL-009", "JTL-021"))).isEqualTo("Colossus");
    }

    @Test
    void testToSwudbJson() {
        final var deck = createDeck("JTL-009", "JTL-026",
                Bags.immutable.ofOccurrences("JTL-143", 2),
                Bags.immutable.ofOccurrences("JTL-045", 3)
        );
        assertThat(svc.toSwudbJson(deck)).isEqualTo("""
                ---
                metadata:
                  name: "Boba Fett (JTL) - Red"
                  author: "Me"
                leader:
                  id: "JTL_009"
                  count: 1
                base:
                  id: "JTL_026"
                  count: 1
                deck:
                - id: "JTL_143"
                  count: 2
                sideboard:
                - id: "JTL_045"
                  count: 3
                """);
    }

    @Test
    void testLoadMelee() throws IOException {
        final var meleeRes = new ClassPathResource("/melee-deck.html");
        stubFor(get(urlEqualTo("/melee")).willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE).withBody(meleeRes.getContentAsByteArray())));

        final var meleeUri = URI.create("http://melee.127.0.0.1.nip.io:" + wiremockPort + "/melee");
        final var deck = svc.load(meleeUri, true);
        assertThat(deck.leader()).isEqualTo("JTL-009");
        assertThat(deck.base()).isEqualTo("JTL-021");
        assertThat(deck.main().occurrencesOf("JTL-045")).isEqualTo(3);
        assertThat(deck.main().occurrencesOf("JTL-143")).isEqualTo(1);
        assertThat(deck.sideboard().occurrencesOf("JTL-143")).isEqualTo(2);

        final var deck2 = svc.load(meleeUri);
        assertThat(deck2.main()).isEqualTo(deck.main());
        assertThat(deck2.sideboard()).isEqualTo(deck.sideboard());
    }

    @Test
    void testLoadingWithJackson() throws IOException {
        final var deck = createDeck("JTL-009", "JTL-026",
                Bags.immutable.ofOccurrences("JTL-143", 2),
                Bags.immutable.ofOccurrences("JTL-045", 3)
        );

        final var objectMapper = new ObjectMapper(new YAMLFactory());
        final var out = new StringWriter(512);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, deck);
        final var yamlOut = out.getBuffer().toString();
        assertThat(yamlOut).isEqualTo("""
                ---
                source: "http://foo.bar"
                author: "Me"
                format: "premier"
                leader: "JTL-009"
                base: "JTL-026"
                main:
                - card: "JTL-143"
                  count: 2
                sideboard:
                - card: "JTL-045"
                  count: 3
                """);

        final var deck2 = objectMapper.readValue(yamlOut, Deck.class);
        assertThat(deck2.main()).isEqualTo(deck.main());
        assertThat(deck2.sideboard()).isEqualTo(deck.sideboard());
    }

    private static Deck createDeck(String leader, String base) {
        return new Deck(URI.create("http://foo.bar"), "Me", Format.PREMIER, leader, base, Bags.immutable.empty(), Bags.immutable.empty());
    }

    private static Deck createDeck(String leader, String base, Bag main, Bag sideboard) {
        return new Deck(URI.create("http://foo.bar"), "Me", Format.PREMIER, leader, base, main, sideboard);
    }
}

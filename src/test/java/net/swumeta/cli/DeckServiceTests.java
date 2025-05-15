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
import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Deck;
import org.eclipse.collections.api.factory.Bags;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONCompareMode;
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
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
class DeckServiceTests {
    @Autowired
    private DeckService svc;
    @Autowired
    private TestHelper helper;
    @Value("${wiremock.server.port}")
    private int wiremockPort;

    @Test
    void testFormatName() {
        assertThat(svc.formatName(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026")))).isEqualTo("Boba Fett (JTL) - Red");
        assertThat(svc.formatName(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-021")))).isEqualTo("Boba Fett (JTL) - Colossus");
    }

    @Test
    void testFormatNameDagobah() {
        assertThat(svc.formatName(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-021")))).isEqualTo("Boba Fett (JTL) - Blue");
    }

    @Test
    void testFormatLeader() {
        assertThat(svc.formatLeader(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026")))).isEqualTo("Boba Fett (JTL)");
    }

    @Test
    void testFormatBase() {
        assertThat(svc.formatBase(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026")))).isEqualTo("Red");
        assertThat(svc.formatBase(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-021")))).isEqualTo("Colossus");
    }

    @Test
    void testFormatBaseDagobah() {
        assertThat(svc.formatBase(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-021")))).isEqualTo("Blue");
    }

    @Test
    void testGetArchetypeDagobah() {
        final var archetype = svc.getArchetype(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-021")));
        assertThat(archetype.base()).isNull();
        assertThat(archetype.aspect()).isEqualTo(Card.Aspect.VIGILANCE);
    }

    @Test
    void testFormatArchetypeDagobah() {
        final var archetype = svc.getArchetype(helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-021")));
        assertThat(svc.formatArchetype(archetype)).isEqualTo("Boba Fett (JTL) - Blue");
    }

    @Test
    void testToSwudbJson() throws JSONException {
        final var deck = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"),
                Bags.immutable.ofOccurrences(
                        Card.Id.valueOf("JTL-045"), 2,
                        Card.Id.valueOf("JTL-143"), 2
                ),
                Bags.immutable.ofOccurrences(
                        Card.Id.valueOf("JTL-045"), 1,
                        Card.Id.valueOf("JTL-143"), 1
                )
        );
        final var swdbJson = """
                {
                  "metadata" : {
                    "name" : "Boba Fett (JTL) - Red",
                    "author" : "Me"
                  },
                  "leader" : {
                    "id" : "JTL_009",
                    "count" : 1
                  },
                  "base" : {
                    "id" : "JTL_026",
                    "count" : 1
                  },
                  "deck" : [ {
                    "id" : "JTL_045",
                    "count" : 2
                  }, {
                    "id" : "JTL_143",
                    "count" : 2
                  } ],
                  "sideboard" : [ {
                    "id" : "JTL_045",
                    "count" : 1
                  }, {
                    "id" : "JTL_143",
                    "count" : 1
                  } ]
                }
                """;
        assertEquals(swdbJson, svc.toSwudbJson(deck), JSONCompareMode.STRICT);

        final var deck2 = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"),
                Bags.immutable.ofOccurrences(
                        Card.Id.valueOf("JTL-143"), 2,
                        Card.Id.valueOf("JTL-045"), 2
                ),
                Bags.immutable.ofOccurrences(
                        Card.Id.valueOf("JTL-143"), 1,
                        Card.Id.valueOf("JTL-045"), 1
                )
        );
        // Check that the output is always the same.
        assertEquals(swdbJson, svc.toSwudbJson(deck2), JSONCompareMode.STRICT);
    }

    @Test
    void testLoadMelee() throws IOException {
        final var meleeRes = new ClassPathResource("/melee-deck.html");
        stubFor(get(urlEqualTo("/melee")).willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE).withBody(meleeRes.getContentAsByteArray())));

        final var om = new ObjectMapper();
        final var meleeDeckDetails = """
                {
                    "Standings":[],
                    "Team":{
                        "Name":"Foo",
                        "Username":"Foo",
                        "Rank":"2",
                        "MatchRecord":"1-1-0",
                        "Points":"22"
                    },
                    "Matches":[
                        {
                            "Round":1,
                            "Opponent":"Maudor",
                            "OpponentUsername":"Maudor",
                            "OpponentDecklistGuid":"75b9f198-1157-489a-b990-b2bd016cea91",
                            "OpponentDecklistName":"Cassian Andor, Dedicated to the Rebellion - Colossus",
                            "Result":"Maudor won 2-0-0"
                        },
                        {
                            "Round":2,
                            "Opponent":"cedou1002",
                            "OpponentUsername":"cedou1002",
                            "OpponentDecklistGuid":"42c5527c-b781-41a7-977c-b2bc0114d61c",
                            "OpponentDecklistName":"Anakin Skywalker, What it Takes to Win - Colossus",
                            "Result":"Foo won 1-0-0"
                       }
                    ]
                }
                """
                .replace("\n", "")
                .replace("\r", "");
        final var meleeDeckWrapper = """
                {
                  "Error": false,
                  "Errors": null,
                  "Code": 200,
                  "Message": "",
                  "Json": %s,
                  "Redirect": null
                }
                """.formatted(om.writeValueAsString(meleeDeckDetails.trim()));
        stubFor(get(urlEqualTo("/Decklist/GetTournamentViewData/melee")).willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(meleeDeckWrapper)));

        final var meleeUri = URI.create("http://melee.127.0.0.1.nip.io:" + wiremockPort + "/melee");
        final var deck = svc.load(meleeUri);
        assertThat(deck.leader()).isEqualTo(Card.Id.valueOf("JTL-009"));
        assertThat(deck.base()).isEqualTo(Card.Id.valueOf("JTL-021"));
        assertThat(deck.main().occurrencesOf(Card.Id.valueOf("JTL-045"))).isEqualTo(3);
        assertThat(deck.main().occurrencesOf(Card.Id.valueOf("JTL-143"))).isEqualTo(1);
        assertThat(deck.main().occurrencesOf(Card.Id.valueOf("SOR-225"))).isEqualTo(2);
        assertThat(deck.sideboard().occurrencesOf(Card.Id.valueOf("JTL-143"))).isEqualTo(2);

        final var deck2 = svc.load(meleeUri);
        assertThat(deck2.main()).isEqualTo(deck.main());
        assertThat(deck2.sideboard()).isEqualTo(deck.sideboard());
    }

    @Test
    void testLoadingWithJackson() throws IOException {
        final var deck = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("JTL-026"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 2),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 3)
        );

        final var objectMapper = new ObjectMapper(new YAMLFactory());
        final var out = new StringWriter(512);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, deck);
        final var yamlOut = out.getBuffer().toString();
        assertThat(yamlOut).isEqualTo("""
                ---
                source: "%s"
                player: "Me"
                format: "premier"
                leader: "JTL-009"
                base: "JTL-026"
                main:
                - card: "JTL-143"
                  count: 2
                sideboard:
                - card: "JTL-045"
                  count: 3
                matchRecord: "0-0-0"
                """.formatted(deck.source()));

        final var deck2 = objectMapper.readValue(yamlOut, Deck.class);
        assertThat(deck2.main()).isEqualTo(deck.main());
        assertThat(deck2.sideboard()).isEqualTo(deck.sideboard());
    }

    @Test
    void testFormatBaseAlias() {
        final var deck = helper.createDeck(Card.Id.valueOf("JTL-009"), Card.Id.valueOf("SOR-022"),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-143"), 2),
                Bags.immutable.ofOccurrences(Card.Id.valueOf("JTL-045"), 3)
        );
        assertThat(svc.formatBase(deck)).isEqualTo("ECL");
    }
}

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

import net.swumeta.cli.model.Card;
import net.swumeta.cli.model.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CardDownloaderService {
    private final Logger logger = LoggerFactory.getLogger(CardDownloaderService.class);
    private final AppConfig config;
    private final RestClient client;
    private final RetryTemplate retryTemplate;

    CardDownloaderService(AppConfig config, RestClient client, RetryTemplate retryTemplate) {
        this.config = config;
        this.client = client;
        this.retryTemplate = retryTemplate;
    }

    public interface Handler {
        void onCardDownloaded(Card card);
    }

    public void downloadCards(Handler handler) {
        for (final URI uriTemplate : config.cards()) {
            for (int page = 1; ; page += 1) {
                final String uri = uriTemplate.toASCIIString().replace("_PAGE_", String.valueOf(page));
                logger.debug("Downloading cards from URI: {}", uri);
                final var resp = downloadCards(uri);
                for (final JsonCard card : resp.data) {
                    final var set = toSet(card.attributes.expansion.data.id);
                    if (set.isEmpty()) {
                        logger.trace("Unsupported set: {}", card.attributes.expansion.data.id);
                        continue;
                    }
                    final var type = toCardType(card.attributes.type.data.id);
                    if (type.isEmpty()) {
                        logger.trace("Unsupported card type: {}", card.attributes.type.data.id);
                        continue;
                    }
                    final var rarity = toRarity(card.attributes.rarity.data.id);
                    if (rarity.isEmpty()) {
                        logger.trace("Unsupported rarity: {}", card.attributes.rarity.data.id);
                        continue;
                    }
                    final var arena = toArena(card);
                    final var c = new Card(
                            set.get(),
                            card.attributes.cardNumber,
                            type.get(),
                            rarity.get(),
                            arena.orElseGet(() -> null),
                            toAspects(card),
                            card.attributes.cost,
                            card.attributes.title,
                            trimToNull(card.attributes.subtitle),
                            getArtUri(card.attributes.artFront, "card"),
                            getArtUri(card.attributes.artThumbnail.data != null ? card.attributes.artThumbnail : card.attributes.artFront, "thumbnail")
                    );
                    handler.onCardDownloaded(c);
                }
                if (resp.meta.pagination.page >= resp.meta.pagination.pageCount) {
                    break;
                }
            }
        }
    }

    private JsonRoot downloadCards(String uri) {
        return retryTemplate.execute(ctx -> {
            return client.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonRoot.class);
        });
    }

    private static String trimToNull(String s) {
        final var trimmed = s != null ? s.strip() : null;
        return StringUtils.hasLength(trimmed) ? trimmed : null;
    }

    private static Optional<Set> toSet(int expansionNumber) {
        return switch (expansionNumber) {
            case 2, 4 -> Optional.of(Set.SOR);
            case 8, 10 -> Optional.of(Set.SHD);
            case 18, 20 -> Optional.of(Set.TWI);
            case 23, 27 -> Optional.of(Set.JTL);
            default -> Optional.empty();
        };
    }

    private static Optional<Card.Type> toCardType(int type) {
        return switch (type) {
            case 4 -> Optional.of(Card.Type.LEADER);
            case 7 -> Optional.of(Card.Type.BASE);
            case 8 -> Optional.of(Card.Type.EVENT);
            case 9 -> Optional.of(Card.Type.UNIT);
            case 10 -> Optional.of(Card.Type.UPGRADE);
            default -> Optional.empty();
        };
    }

    private static Optional<Card.Arena> toArena(JsonCard card) {
        if (card.attributes.arenas.data.isEmpty()) {
            return Optional.empty();
        }
        return switch (card.attributes.arenas.data.get(0).id) {
            case 2 -> Optional.of(Card.Arena.GROUND);
            case 7 -> Optional.of(Card.Arena.SPACE);
            default -> Optional.empty();
        };
    }

    private static Optional<Card.Rarity> toRarity(int type) {
        return switch (type) {
            case 2 -> Optional.of(Card.Rarity.COMMON);
            case 7 -> Optional.of(Card.Rarity.UNCOMMON);
            case 12 -> Optional.of(Card.Rarity.RARE);
            case 17 -> Optional.of(Card.Rarity.LEGENDARY);
            case 22 -> Optional.of(Card.Rarity.SPECIAL);
            default -> Optional.empty();
        };
    }

    private static List<Card.Aspect> toAspects(JsonCard card) {
        if (card.attributes.aspects == null || card.attributes.aspects.data.isEmpty()) {
            return List.of();
        }
        return card.attributes.aspects.data.stream().map((a -> switch (a.id) {
            case 2 -> Card.Aspect.VIGILANCE;
            case 7 -> Card.Aspect.COMMAND;
            case 12 -> Card.Aspect.AGGRESSION;
            case 17 -> Card.Aspect.CUNNING;
            case 22 -> Card.Aspect.HEROISM;
            case 27 -> Card.Aspect.VILLAINY;
            default -> null;
        })).filter(Objects::nonNull).toList();
    }

    private static URI getArtUri(JsonArt art, String artType) {
        if (art == null) {
            return null;
        }
        final var artFormat = art.data.attributes.formats.get(artType);
        if (artFormat == null) {
            return null;
        }
        return artFormat.url;
    }

    private record JsonRoot(
            JsonCard[] data,
            JsonMeta meta
    ) {
    }

    private record JsonMeta(
            JsonPagination pagination
    ) {
    }

    private record JsonPagination(
            int page,
            int pageSize,
            int pageCount
    ) {
    }

    private record JsonCard(
            JsonCardAttributes attributes
    ) {
    }

    private record JsonCardAttributes(
            String title,
            String subtitle,
            int cardNumber,
            int cost,
            JsonType type,
            JsonRarity rarity,
            JsonArenas arenas,
            JsonAspects aspects,
            JsonExpansion expansion,
            JsonArt artFront,
            JsonArt artThumbnail
    ) {
    }

    private record JsonArt(
            JsonArtData data
    ) {
    }

    private record JsonArtData(
            JsonArtDataAttributes attributes
    ) {
    }

    private record JsonArtDataAttributes(
            Map<String, JsonArtFormat> formats
    ) {
    }

    private record JsonArtFormat(
            URI url
    ) {
    }

    private record JsonExpansion(
            JsonExpansionData data
    ) {
    }

    private record JsonExpansionData(
            int id
    ) {
    }

    private record JsonType(
            JsonTypeData data
    ) {
    }

    private record JsonTypeData(
            int id
    ) {
    }

    private record JsonRarity(
            JsonRarityData data
    ) {
    }

    private record JsonRarityData(
            int id
    ) {
    }

    private record JsonArenas(
            List<JsonArenaData> data
    ) {
    }

    private record JsonArenaData(
            int id
    ) {
    }

    private record JsonAspects(
            List<JsonAspectData> data
    ) {
    }

    private record JsonAspectData(
            int id
    ) {
    }
}

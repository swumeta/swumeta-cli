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
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.swumeta.cli.model.Card;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.impl.factory.Multimaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CardDatabaseService {
    private final Logger logger = LoggerFactory.getLogger(CardDatabaseService.class);
    private final AppConfig config;
    private final ObjectMapper objectMapper;
    private final MutableMultimap<String, File> cardsByName = Multimaps.mutable.set.of();
    private final LoadingCache<Card.Id, Card> cardByIdCache;

    CardDatabaseService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.objectMapper.findAndRegisterModules();
        cardByIdCache = Caffeine.newBuilder().weakKeys().weakValues().build(this::loadById);
    }

    public Card findById(Card.Id id) {
        Assert.notNull(id, "Card id must not be null");
        try {
            return cardByIdCache.get(id);
        } catch (Exception e) {
            throw new AppException("Failed to lookup card by id: " + id, e);
        }
    }

    public String formatName(Card.Id id) {
        final var card = findById(id);
        return "%s (%s)".formatted(card.name(), card.set());
    }

    public URI toIcon(Card.Id id) {
        final var imagesDir = new File(config.output(), "images");
        final var iconsDir = new File(imagesDir, "icons");
        if (!iconsDir.exists()) {
            iconsDir.mkdirs();
        }
        final var iconFile = new File(iconsDir, id.toString() + ".png");
        if (!iconFile.exists()) {
            final var card = findById(id);
            if (card.art() == null) {
                throw new AppException("Card art is not available: " + id);
            }

            final BufferedImage art;
            try {
                logger.debug("Loading art for card {}: {}", id, card.art());
                art = ImageIO.read(card.art().toURL());
            } catch (IOException e) {
                throw new AppException("Failed to load card art: " + id, e);
            }

            final var icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            final var g = icon.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                        RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_DITHERING,
                        RenderingHints.VALUE_DITHER_ENABLE);
                g.drawImage(art, 0, 0, icon.getWidth(), icon.getHeight(),
                        70, 40, icon.getWidth(), icon.getHeight(), null);
            } finally {
                g.dispose();
            }

            try {
                logger.debug("Saving icon art for card {} to file: {}", id, iconFile);
                ImageIO.write(icon, "PNG", iconFile);
            } catch (IOException e) {
                throw new AppException("Failed to write card icon " + id + " to file: " + iconFile, e);
            }
        }
        return UriComponentsBuilder.fromUri(config.base()).pathSegment("images", "icons", iconFile.getName()).build().toUri();
    }

    private Card loadById(Card.Id id) {
        Assert.notNull(id, "Card id must not be null");
        logger.trace("Loading card by id: {}", id);

        final var cardSetDir = new File(getCardsDir(), id.set().name());
        final var cardFile = new File(cardSetDir, "%s.yaml".formatted(id));
        try {
            return readCardFile(cardFile);
        } catch (IOException e) {
            throw new AppException("Failed to card " + id, e);
        }
    }

    public Set<Card> findByName(String name, @Nullable String title) {
        Assert.notNull(name, "Argument name must not be null");
        initIndex();

        title = trimToNull(title);
        final var cardFiles = cardsByName.get(name);
        if (cardFiles == null || cardFiles.isEmpty()) {
            logger.trace("Unknown card: name={}, title={}", name, title);
            return Set.of();
        }

        final var cards = new HashSet<Card>(1);
        for (final var cardFile : cardFiles) {
            if (!cardFile.exists()) {
                logger.trace("Card file not found: {}", cardFile);
                continue;
            }
            final Card card;
            try {
                card = readCardFile(cardFile);
            } catch (IOException e) {
                throw new AppException("Failed to read card file: " + cardFile, e);
            }
            if (title == null || title.equals(card.title())) {
                cards.add(card);
            }
        }
        return cards;
    }

    private Card readCardFile(File cardFile) throws IOException {
        try (final InputStream in = new FileInputStream(cardFile)) {
            logger.trace("Reading card file: {}", cardFile);
            return objectMapper.readerFor(Card.class).readValue(in);
        }
    }

    private void initIndex() {
        if (!cardsByName.isEmpty()) {
            return;
        }

        logger.debug("Initializing card database index");
        final var cardFiles = new ArrayList<File>(256);
        listFilesRecursively(getCardsDir(), cardFiles);
        for (final var cardFile : cardFiles) {
            final Card card;
            try {
                card = readCardFile(cardFile);
            } catch (IOException e) {
                throw new AppException("Failed to read card from file: " + cardFile, e);
            }
            cardsByName.put(card.name(), cardFile);
        }
    }

    private static void listFilesRecursively(File directory, List<File> filesList) {
        final var files = directory.listFiles();
        if (files != null) {
            for (final var file : files) {
                if (file.isFile() && file.getName().endsWith(".yaml")) {
                    filesList.add(file);
                } else if (file.isDirectory()) {
                    listFilesRecursively(file, filesList);
                }
            }
        }
    }

    public void save(Card card) {
        Assert.notNull(card, "Card must not be null");
        initIndex();

        final var cardsDir = getCardsDir();
        if (!cardsDir.exists()) {
            cardsDir.mkdirs();
        }
        final var setDir = new File(cardsDir, card.set().name());
        if (!setDir.exists()) {
            setDir.mkdirs();
        }
        final var cardFile = new File(setDir, card.id() + ".yaml");
        logger.debug("Saving card: {}", cardFile);
        try {
            objectMapper.writerFor(Card.class).withDefaultPrettyPrinter().writeValue(cardFile, card);
        } catch (IOException e) {
            throw new AppException("Failed to save card: " + card.id(), e);
        }
        cardsByName.put(card.name(), cardFile);
    }

    private static String trimToNull(String s) {
        final var trimmed = s != null ? s.strip() : null;
        return StringUtils.hasLength(trimmed) ? trimmed : null;
    }

    private File getCardsDir() {
        return new File(config.database(), "cards");
    }

    public void clear() {
        cardsByName.clear();
        final var cardsDir = getCardsDir();
        if (cardsDir.exists()) {
            logger.debug("Clearing cards database");
            FileSystemUtils.deleteRecursively(cardsDir);
        }
    }
}

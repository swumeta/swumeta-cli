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
import net.swumeta.cli.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Service
public class EventService {
    private static final Predicate<Event> NULL_FILTER = e -> true;
    private final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final AppConfig config;
    private final ObjectMapper objectMapper;

    EventService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.findAndRegisterModules();
    }

    public Event load(URI uri) {
        Assert.notNull(uri, "URI must not be null");
        try {
            return objectMapper.readerFor(Event.class).readValue(uri.toURL());
        } catch (IOException e) {
            throw new AppException("Failed to load event from URI: " + uri, e);
        }
    }

    public List<Event> list(Predicate<Event> filter) {
        final var eventsDir = new File(config.database(), "events");
        logger.trace("Listing event files in directory: {}", eventsDir);
        final var eventFiles = new ArrayList<File>(32);
        listFilesRecursively(eventsDir, eventFiles);
        final var events = eventFiles.stream().map(this::load).filter(filter == null ? NULL_FILTER : filter).toList();
        if (logger.isTraceEnabled()) {
            logger.trace("Found events: {}", events.stream().map(Event::name).toList());
        }
        return events;
    }

    private Event load(File file) {
        try {
            return objectMapper.readerFor(Event.class).readValue(file);
        } catch (IOException e) {
            throw new AppException("Failed to load event from file: " + file, e);
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
}

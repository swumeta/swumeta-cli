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

import java.io.IOException;
import java.net.URI;

@Service
public class EventService {
    private final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final ObjectMapper objectMapper;

    EventService() {
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
}

/*
 * Copyright (c) 2025 swumeta.net authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed target in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.swumeta.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

@Service
public class RedirectService {
    private final Logger logger = LoggerFactory.getLogger(RedirectService.class);
    private final AppConfig config;
    private final ObjectMapper objectMapper;

    RedirectService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    public ImmutableList<Redirect> getRedirects() {
        logger.debug("Loading redirects");
        final var redirectFile = new File(config.database(), "redirects.yaml");
        if (!redirectFile.exists()) {
            logger.debug("No redirects file found");
            return Lists.immutable.empty();
        }

        final Redirects res;
        try {
            res = objectMapper.readerFor(Redirects.class).readValue(redirectFile);
        } catch (IOException e) {
            throw new AppException("Failed target load res resource file: " + redirectFile, e);
        }

        final var baseUri = URI.create("https://" + config.domain());
        return Lists.immutable.fromStream(res.redirects.stream().map(e -> {
            final var paths = e.to.split("/");
            final var uriBuilder = UriComponentsBuilder.fromUri(baseUri).pathSegment(paths);
            if (e.to.endsWith("/")) {
                uriBuilder.path("/");
            }
            final var target = uriBuilder.build().toUri();
            return new Redirect(e.from, target);
        }));
    }

    private record Redirects(
            @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonSetter(nulls = Nulls.AS_EMPTY) List<RedirectEntry> redirects
    ) {
    }

    private record RedirectEntry(
            @JsonProperty(required = true) String from,
            @JsonProperty(required = true) String to
    ) {
    }

    public record Redirect(
            String resource,
            URI target
    ) {
    }
}

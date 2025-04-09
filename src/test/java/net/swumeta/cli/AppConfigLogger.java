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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
class AppConfigLogger implements CommandLineRunner {
    private final Logger logger = LoggerFactory.getLogger(AppConfigLogger.class);
    private final AppConfig config;

    AppConfigLogger(AppConfig config) {
        this.config = config;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Database dir: {}", config.database());
        logger.info("Cache dir: {}", config.cache());
    }
}

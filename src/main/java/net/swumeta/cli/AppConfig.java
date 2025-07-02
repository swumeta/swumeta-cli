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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;

@ConfigurationProperties(prefix = "swu")
public record AppConfig(
        URI base,
        File database,
        File output,
        File cache,
        int metagameMonths,
        LocalDate metagameLimit
) {
}

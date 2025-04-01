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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class RestConfig {
    @Bean
    RestClient restClient(RestClient.Builder rcb,
                          @Value("${spring.application.name") String appName,
                          BuildProperties build) {
        final var userAgent = appName + "/" + build.getVersion();
        return rcb.clone()
                // Need to set the user agent as curl otherwise melee.gg will forbid requests.
                .defaultHeader(HttpHeaders.USER_AGENT, "curl/8.5.0")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .build();
    }
}

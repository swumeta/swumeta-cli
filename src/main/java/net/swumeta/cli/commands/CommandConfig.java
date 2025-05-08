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

package net.swumeta.cli.commands;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandRegistration;

import java.io.File;

@Configuration(proxyBeanMethods = false)
class CommandConfig {
    @Bean
    CommandRegistration generateSite(GenerateSiteCommand cmd) {
        return CommandRegistration.builder()
                .command("generate-site")
                .description("Generate website")
                .withOption().longNames("output").shortNames('o').description("Output directory").type(File.class).defaultValue("public").required(false).and()
                .withTarget().consumer(ctx -> {
                    final var output = (File) ctx.getOptionValue("output");
                    cmd.run(output);
                })
                .and().build();
    }

    @Bean
    CommandRegistration serve(ServeFilesCommand cmd) {
        return CommandRegistration.builder()
                .command("serve")
                .description("Serve files over HTTP")
                .withOption().longNames("directory").shortNames('d').description("Base directory").type(File.class).defaultValue("public").required(false).and()
                .withTarget().consumer(ctx -> {
                    final var dir = (File) ctx.getOptionValue("directory");
                    cmd.run(dir);
                })
                .and().build();
    }

    @Bean
    CommandRegistration downloadCards(DownloadCardsCommand cmd) {
        return CommandRegistration.builder()
                .command("download-cards")
                .description("Download cards from the online SWU database")
                .withTarget().consumer(ctx -> {
                    cmd.run();
                })
                .and().build();
    }

    @Bean
    CommandRegistration findCards(FindCardsCommand cmd) {
        return CommandRegistration.builder()
                .command("find-cards")
                .description("Find cards")
                .withOption().longNames("name").shortNames('n').description("Card name").required().and()
                .withOption().longNames("title").shortNames('t').description("Card title, if any").required(false).and()
                .withTarget().consumer(ctx -> {
                    final var title = (String) ctx.getOptionValue("name");
                    final var subtitle = (String) ctx.getOptionValue("title");
                    cmd.run(title, subtitle);
                })
                .and().build();
    }

    @Bean
    CommandRegistration syncEvents(SyncEventsCommand cmd) {
        return CommandRegistration.builder()
                .command("sync-events")
                .description("Sync events")
                .withTarget().consumer(ctx -> {
                    cmd.run();
                })
                .and().build();
    }
}

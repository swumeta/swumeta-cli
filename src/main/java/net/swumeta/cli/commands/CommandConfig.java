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
import java.net.URI;

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
    CommandRegistration getDeck(GetDeckCommand cmd) {
        return CommandRegistration.builder()
                .command("get-deck")
                .description("Display deck details from an URI")
                .withOption().longNames("url").shortNames('u').description("Deck URL").type(URI.class).required().and()
                .withTarget().consumer(ctx -> {
                    final var uri = (URI) ctx.getOptionValue("url");
                    cmd.run(uri);
                })
                .and().build();
    }

    @Bean
    CommandRegistration getEvent(GetEventCommand cmd) {
        return CommandRegistration.builder()
                .command("get-event")
                .description("Display event details")
                .withOption().longNames("file").shortNames('f').description("Event manifest").type(File.class).required().and()
                .withTarget().consumer(ctx -> {
                    final var file = (File) ctx.getOptionValue("file");
                    cmd.run(file);
                })
                .and().build();
    }

    @Bean
    CommandRegistration getMeleeDecks(GetMeleeDecksCommand cmd) {
        return CommandRegistration.builder()
                .command("get-melee-decks")
                .description("Get the list of decks from a melee.gg page")
                .withOption().longNames("url").shortNames('u').description("melee.gg URL").type(URI.class).required().and()
                .withOption().longNames("max").shortNames('m').description("Maximum number of decks to retrieve").required(false).type(Integer.TYPE).defaultValue("0").and()
                .withTarget().consumer(ctx -> {
                    final var url = (URI) ctx.getOptionValue("url");
                    final var max = (Integer) ctx.getOptionValue("max");
                    cmd.run(url, max);
                })
                .and().build();
    }
}

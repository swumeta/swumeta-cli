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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Component
public class StaticResources {
    private final Logger logger = LoggerFactory.getLogger(StaticResources.class);

    @Value("classpath:/static/css/styles.css")
    private Resource stylesCss;
    @Value("classpath:/static/images/logo.svg")
    private Resource logoSvg;
    @Value("classpath:/static/images/bluesky.svg")
    private Resource blueskySvg;
    @Value("classpath:/static/images/starfield.jpg")
    private Resource starfieldJpg;
    @Value("classpath:/static/images/new-label.png")
    private Resource newLabelPng;
    @Value("classpath:/static/images/aspect_aggression.png")
    private Resource aspectAggressionPng;
    @Value("classpath:/static/images/aspect_command.png")
    private Resource aspectCommandPng;
    @Value("classpath:/static/images/aspect_cunning.png")
    private Resource aspectCunningPng;
    @Value("classpath:/static/images/aspect_heroism.png")
    private Resource aspectHeroismPng;
    @Value("classpath:/static/images/aspect_vigilance.png")
    private Resource aspectVigilancePng;
    @Value("classpath:/static/images/aspect_villainy.png")
    private Resource aspectVillainyPng;
    @Value("classpath:/static/android-chrome-192x192.png")
    private Resource androidChrome192Png;
    @Value("classpath:/static/android-chrome-512x512.png")
    private Resource androidChrome512Png;
    @Value("classpath:/static/apple-touch-icon.png")
    private Resource appleTouchIconPng;
    @Value("classpath:/static/favicon.ico")
    private Resource faviconIco;
    @Value("classpath:/static/favicon-16x16.png")
    private Resource favicon16Png;
    @Value("classpath:/static/favicon-32x32.png")
    private Resource favicon32Png;
    @Value("classpath:/static/site.webmanifest")
    private Resource siteManifest;
    @Value("classpath:/static/js/ga.js")
    private Resource gaJs;
    @Value("classpath:/static/js/charts.js")
    private Resource chartsJs;
    @Value("classpath:/static/js/winrates.js")
    private Resource winratesJs;

    public void copyToDirectory(File targetDir) throws IOException {
        final var cssDir = new File(targetDir, "css");
        cssDir.mkdirs();
        copyResourceToFile(stylesCss, cssDir);

        final var imagesDir = new File(targetDir, "images");
        imagesDir.mkdirs();
        copyResourceToFile(logoSvg, imagesDir);
        copyResourceToFile(blueskySvg, imagesDir);
        copyResourceToFile(starfieldJpg, imagesDir);
        copyResourceToFile(newLabelPng, imagesDir);
        copyResourceToFile(aspectAggressionPng, imagesDir);
        copyResourceToFile(aspectCommandPng, imagesDir);
        copyResourceToFile(aspectCunningPng, imagesDir);
        copyResourceToFile(aspectHeroismPng, imagesDir);
        copyResourceToFile(aspectVigilancePng, imagesDir);
        copyResourceToFile(aspectVillainyPng, imagesDir);

        copyResourceToFile(androidChrome192Png, targetDir);
        copyResourceToFile(androidChrome512Png, targetDir);
        copyResourceToFile(appleTouchIconPng, targetDir);
        copyResourceToFile(faviconIco, targetDir);
        copyResourceToFile(favicon16Png, targetDir);
        copyResourceToFile(favicon32Png, targetDir);
        copyResourceToFile(siteManifest, targetDir);

        final var jsDir = new File(targetDir, "js");
        jsDir.mkdirs();
        copyResourceToFile(gaJs, jsDir);
        copyResourceToFile(chartsJs, jsDir);
        copyResourceToFile(winratesJs, jsDir);
    }

    private void copyResourceToFile(Resource res, File targetDir) throws IOException {
        final var targetFile = new File(targetDir, res.getFilename());
        try (final var in = res.getInputStream();
             final var out = new FileOutputStream(targetFile)) {
            logger.debug("Copying resource: {}", targetFile.getName());
            StreamUtils.copy(in, out);
        }
    }
}

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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

@Component
class ServeFilesCommand {
    private final Logger logger = LoggerFactory.getLogger(ServeFilesCommand.class);

    void run(File dir) {
        final int port = 8080;
        logger.info("Listening on port: {}", port);

        try {
            final var server = HttpServer.create(new InetSocketAddress(port), 0);

            // Add a handler to serve static files
            server.createContext("/", new FileHandler(dir));

            // Use Virtual Threads (Java 21 feature)
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serve resources on port " + port, e);
        }
    }

    // Handler for serving files
    private static class FileHandler implements HttpHandler {
        private final File basePath;

        public FileHandler(File basePath) {
            this.basePath = basePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();

            // Normalize the path to prevent path traversal attacks
            requestPath = requestPath.replaceAll("\\.\\./", "").replaceAll("//", "/");
            if (requestPath.equals("/")) {
                requestPath = "/index.html"; // Default page
            }

            Path filePath = Paths.get(basePath.getAbsolutePath(), requestPath);
            File file = filePath.toFile();

            if (file.exists() && file.isFile()) {
                // Determine MIME type
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                byte[] fileData = Files.readAllBytes(filePath);

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, fileData.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileData);
                }
            } else {
                // File not found
                String response = "404 Not Found: " + requestPath;
                exchange.sendResponseHeaders(404, response.length());

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}

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
import net.swumeta.cli.AppConfig;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

@Component
class
ServeFilesCommand {
    private final Logger logger = LoggerFactory.getLogger(ServeFilesCommand.class);
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        // Common MIME types configuration
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("xml", "application/xml; charset=UTF-8");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("txt", "text/plain; charset=UTF-8");
        MIME_TYPES.put("svg", "image/svg+xml");
    }

    private final AppConfig config;

    ServeFilesCommand(AppConfig config) {
        this.config = config;
    }


    void run() {
        final int port = 8080;
        logger.info("Listening on port: {}", port);

        try {
            final var server = HttpServer.create(new InetSocketAddress(port), 0);

            // Add a handler to serve static files
            server.createContext("/", new FileHandler(config.output()));

            // Use Virtual Threads (Java 21 feature)
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serve resources on port " + port, e);
        }
    }

    static class FileHandler implements HttpHandler {
        private final File dir;

        FileHandler(File dir) {
            this.dir = dir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();

            if (!requestMethod.equals("GET")) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            Path filePath = getFilePath(requestPath);

            if (filePath == null) {
                sendError(exchange, 404, "Resource Not Found");
                return;
            }

            try {
                byte[] fileContent = Files.readAllBytes(filePath);
                String mimeType = getMimeType(filePath.toString());

                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.sendResponseHeaders(200, fileContent.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileContent);
                }
            } catch (IOException e) {
                sendError(exchange, 500, "Server Error: " + e.getMessage());
            }
        }

        private Path getFilePath(String requestPath) {
            // Clean the request path
            requestPath = requestPath.replaceAll("\\.\\.", "");
            if (requestPath.endsWith("/")) {
                requestPath += "index.html";
            }

            Path filePath = Paths.get(dir.getAbsolutePath(), requestPath);
            File file = filePath.toFile();

            if (file.exists() && file.isFile()) {
                return filePath;
            } else if (file.isDirectory()) {
                // Check if index.html exists in the directory
                Path indexPath = Paths.get(filePath.toString(), "index.html");
                if (indexPath.toFile().exists() && indexPath.toFile().isFile()) {
                    return indexPath;
                }
            }

            return null;
        }

        private String getMimeType(String filePath) {
            int lastDotIndex = filePath.lastIndexOf('.');
            if (lastDotIndex > 0) {
                String extension = filePath.substring(lastDotIndex + 1).toLowerCase();
                return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
            }
            return "application/octet-stream";
        }

        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, message.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(message.getBytes());
            }
        }
    }
}

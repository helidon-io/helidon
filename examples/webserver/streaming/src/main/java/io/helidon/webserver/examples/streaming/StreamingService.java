/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webserver.examples.streaming;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Logger;

import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import static io.helidon.webserver.examples.streaming.Main.LARGE_FILE_RESOURCE;

/**
 * StreamingService class.
 * Uses a {@link java.io.InputStream} and {@link java.io.OutputStream} for uploading and downloading files.
 */
public class StreamingService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(StreamingService.class.getName());

    private final Path filePath;

    StreamingService() {
        try {
            filePath = Paths.get(Objects.requireNonNull(getClass().getResource(LARGE_FILE_RESOURCE)).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/download", this::download)
             .post("/upload", this::upload);
    }

    private void upload(ServerRequest request, ServerResponse response) {
        LOGGER.info("Entering upload ... " + Thread.currentThread());
        try {
            Path tempFilePath = Files.createTempFile("large-file", ".tmp");
            Files.copy(request.content().inputStream(), tempFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("Exiting upload ...");
    }

    private void download(ServerRequest request, ServerResponse response) {
        LOGGER.info("Entering download ..." + Thread.currentThread());
        long length = filePath.toFile().length();
        response.headers().contentLength(length);
        try {
            Files.copy(filePath, response.outputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("Exiting download ...");
    }
}

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

package io.helidon.examples.webserver.streaming;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Logger;

import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * StreamingService class.
 * Uses a {@link java.io.InputStream} and {@link java.io.OutputStream} for uploading and downloading files.
 */
public class StreamingService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(StreamingService.class.getName());

    // Last file uploaded (or default). Since we don't do any locking
    // when operating on the file this example is not safe for concurrent requests.
    private volatile File file;

    StreamingService() {
        // Default download file;
        file = Paths.get(Main.LARGE_FILE_PATH).toFile();
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
            Files.copy(request.content().inputStream(), tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            file = tempFilePath.toFile();
            response.send("File was stored as " + tempFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("Exiting upload after uploading " + file.length() + " bytes...");
    }

    private void download(ServerRequest request, ServerResponse response) {
        LOGGER.info("Entering download ..." + Thread.currentThread());
        if (!file.canRead()) {
            LOGGER.warning("Can't read file " + file.getAbsolutePath());
            response.status(Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
        long length = file.length();
        response.headers().contentLength(length);
        try {
            Files.copy(file.toPath(), response.outputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("Exiting download after serving " + length + " bytes...");
    }
}

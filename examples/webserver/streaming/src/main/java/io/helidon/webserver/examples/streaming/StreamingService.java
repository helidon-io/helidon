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
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.IoMulti;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.webserver.examples.streaming.Main.LARGE_FILE_RESOURCE;

/**
 * StreamingService class. Uses a {@code Subscriber<RequestChunk>} and a
 * {@code Publisher<ResponseChunk>} for uploading and downloading files.
 */
public class StreamingService implements Service {
    private static final Logger LOGGER = Logger.getLogger(StreamingService.class.getName());
    private final ScheduledExecutorService executor = ScheduledThreadPoolSupplier.create().get();
    private final Path filePath;

    StreamingService() {
        URL resource = getClass().getResource(LARGE_FILE_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Resource not found: " + LARGE_FILE_RESOURCE);
        }
        try {
            filePath = Paths.get(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(Routing.Rules routingRules) {
        routingRules.get("/download", this::download)
                    .post("/upload", this::upload);
    }

    private void upload(ServerRequest request, ServerResponse response) {
        LOGGER.info("Entering upload ... " + Thread.currentThread());
        Path tempFilePath = createTempFile("large-file", ".tmp");
        request.content()
               .map(DataChunk::data)
               .flatMapIterable(Arrays::asList)
               .to(IoMulti.writeToFile(tempFilePath)
                          .executor(executor)
                          .build());
        LOGGER.info("Exiting upload ...");
    }

    private void download(ServerRequest request, ServerResponse response) {
        LOGGER.info("Entering download ..." + Thread.currentThread());
        long length = filePath.toFile().length();
        response.headers().contentLength(length);
        response.send(IoMulti.multiFromByteChannelBuilder(newByteChannel(filePath))
                             .executor(executor)
                             .build());
        LOGGER.info("Exiting download ...");
    }

    @SuppressWarnings("SameParameterValue")
    private static Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(prefix, suffix);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static ReadableByteChannel newByteChannel(Path path) {
        try {
            return Files.newByteChannel(path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

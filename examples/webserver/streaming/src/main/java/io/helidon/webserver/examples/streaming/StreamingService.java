/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

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

    private final Path filePath;

    StreamingService() {
        try {
            filePath = Paths.get(getClass().getResource(LARGE_FILE_RESOURCE).toURI());
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
        request.content().subscribe(new ServerFileWriter(response));
        LOGGER.info("Exiting upload ...");
    }

    private void download(ServerRequest request, ServerResponse response) {
        LOGGER.info("Entering download ..." + Thread.currentThread());
        long length = filePath.toFile().length();
        response.headers().add("Content-Length", String.valueOf(length));
        response.send(new ServerFileReader(filePath));
        LOGGER.info("Exiting download ...");
    }
}

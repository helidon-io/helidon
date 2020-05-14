/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.examples.media.multipart;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.multipart.common.ContentDisposition;
import io.helidon.media.multipart.common.ReadableBodyPart;
import io.helidon.media.multipart.common.ReadableMultiPart;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.common.http.Http.Status.BAD_REQUEST_400;
import static io.helidon.common.http.Http.Status.NOT_FOUND_404;

/**
 * File service.
 */
public final class FileService implements Service {

    private final JsonBuilderFactory jsonFactory;
    private final Path storage;

    /**
     * Create a new file upload service instance.
     */
    FileService() {
        jsonFactory = Json.createBuilderFactory(Map.of());
        storage = wrap(() -> Files.createTempDirectory("fileupload"));
        System.out.println("Storage: " + storage);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::list)
             .get("/{fname}", this::download)
             .post("/", this::upload);
    }

    private void list(ServerRequest req, ServerResponse res) {
        JsonArrayBuilder arrayBuilder = jsonFactory.createArrayBuilder();
        wrap(() -> Files.walk(storage))
                .filter(Files::isRegularFile)
                .map(storage::relativize)
                .map(Path::toString)
                .forEach(arrayBuilder::add);
        res.send(jsonFactory.createObjectBuilder().add("files", arrayBuilder).build());
    }

    private void download(ServerRequest req, ServerResponse res) {
        Path filePath = storage.resolve(req.path().param("fname"));
        if (!filePath.getParent().equals(storage)) {
            res.status(BAD_REQUEST_400).send("Invalid file name");
            return;
        }
        if (!Files.exists(filePath)) {
            res.status(NOT_FOUND_404).send();
            return;
        }
        if (!Files.isRegularFile(filePath)) {
            res.status(BAD_REQUEST_400).send("Not a file");
            return;
        }
        ResponseHeaders headers = res.headers();
        headers.contentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.put(Http.Header.CONTENT_DISPOSITION, ContentDisposition.builder()
                .filename(filePath.getFileName().toString())
                .build()
                .toString());
        res.send(filePath);
    }

    private void upload(ServerRequest req, ServerResponse res) {
        if (req.queryParams().first("stream").isPresent()) {
            streamUpload(req, res);
        } else {
            bufferedUpload(req, res);
        }
    }

    private void bufferedUpload(ServerRequest req, ServerResponse res) {
        req.content().as(ReadableMultiPart.class).thenAccept(multiPart -> {
            for (ReadableBodyPart part : multiPart.fields("file[]")) {
                part.headers().contentDisposition().filename().map(storage::resolve).ifPresent((file) -> {
                    wrap(() -> Files.write(file, part.as(byte[].class), StandardOpenOption.CREATE_NEW));
                });
            }
            res.status(Http.Status.MOVED_PERMANENTLY_301);
            res.headers().put(Http.Header.LOCATION, "/ui");
            res.send();
        });
    }

    private void streamUpload(ServerRequest req, ServerResponse res) {
        req.content().asStream(ReadableBodyPart.class).subscribe((part) -> {
            // onNext
            part.headers().contentDisposition().filename().map(storage::resolve).ifPresent((file) -> {
                final ByteChannel channel = wrap(() -> Files.newByteChannel(file, StandardOpenOption.CREATE_NEW));
                Multi.from(part.content())
                        .map(DataChunk::data)
                        .forEach((buffer) -> wrap(() -> channel.write(buffer)));
            });
        }, (error) -> {
            // onError
            res.send(error);
        }, () -> {
            // onComplete
            res.status(Http.Status.MOVED_PERMANENTLY_301);
            res.headers().put(Http.Header.LOCATION, "/ui");
            res.send();
        });
    }

    private static <T> T wrap(IOSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static interface IOSupplier<T> {
        T get() throws IOException;
    }
}

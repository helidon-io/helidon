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
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.multipart.ContentDisposition;
import io.helidon.media.multipart.ReadableBodyPart;
import io.helidon.media.multipart.ReadableMultiPart;
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
        storage = createStorage();
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
        listFiles(storage).forEach(arrayBuilder::add);
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
                writeBytes(storage, part.filename(), part.as(byte[].class));
            }
            res.status(Http.Status.MOVED_PERMANENTLY_301);
            res.headers().put(Http.Header.LOCATION, "/ui");
            res.send();
        });
    }

    private void streamUpload(ServerRequest req, ServerResponse res) {
        req.content().asStream(ReadableBodyPart.class)
                .onError(res::send)
                .onComplete(() -> {
                    res.status(Http.Status.MOVED_PERMANENTLY_301);
                    res.headers().put(Http.Header.LOCATION, "/ui");
                    res.send();
                }).forEach((part) -> {
                    if ("file[]".equals(part.name())) {
                        final ByteChannel channel = newByteChannel(storage, part.filename());
                        Multi.create(part.content()).forEach(chunk -> writeChunk(channel, chunk));
                    }
                });
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("fileupload");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Stream<String> listFiles(Path storage) {
        try {
            return Files.walk(storage)
                    .filter(Files::isRegularFile)
                    .map(storage::relativize)
                    .map(Path::toString);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void writeBytes(Path storage, String fname, byte[] bytes) {
        try {
            Files.write(storage.resolve(fname), bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void writeChunk(ByteChannel channel, DataChunk chunk) {
        try {
            channel.write(chunk.data());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            chunk.release();
        }
    }

    private static ByteChannel newByteChannel(Path storage, String fname) {
        try {
            return Files.newByteChannel(storage.resolve(fname),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}

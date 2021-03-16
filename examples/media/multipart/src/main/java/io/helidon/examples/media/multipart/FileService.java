/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
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
import io.helidon.media.multipart.ContentDisposition;
import io.helidon.media.multipart.ReadableBodyPart;
import io.helidon.media.multipart.ReadableMultiPart;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * File service.
 */
public final class FileService implements Service {

    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());
    private final FileStorage storage;

    /**
     * Create a new file upload service instance.
     */
    FileService() {
        storage = new FileStorage();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::list)
             .get("/{fname}", this::download)
             .post("/", this::upload);
    }

    private void list(ServerRequest req, ServerResponse res) {
        JsonArrayBuilder arrayBuilder = JSON_FACTORY.createArrayBuilder();
        storage.listFiles().forEach(arrayBuilder::add);
        res.send(JSON_FACTORY.createObjectBuilder().add("files", arrayBuilder).build());
    }

    private void download(ServerRequest req, ServerResponse res) {
        Path filePath = storage.lookup(req.path().param("fname"));
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
                writeBytes(storage.create(part.filename()), part.as(byte[].class));
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
                        final ByteChannel channel = newByteChannel(storage.create(part.filename()));
                        part.content()
                                .forEach(chunk -> writeChunk(channel, chunk))
                                .thenAccept(it -> closeChannel(channel));
                    }
                });
    }

    private static void writeBytes(Path file, byte[] bytes) {
        try {
            Files.write(file, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void writeChunk(ByteChannel channel, DataChunk chunk) {
        try {
            for (ByteBuffer byteBuffer : chunk.data()) {
                channel.write(byteBuffer);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            chunk.release();
        }
    }

    private void closeChannel(ByteChannel channel) {
        try {
            channel.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static ByteChannel newByteChannel(Path file) {
        try {
            return Files.newByteChannel(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

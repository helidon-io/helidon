/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalLong;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.ContentDisposition;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Media support for Path.
 * This needs to be a proper media support, as encoding should be provided when sending strings,
 * and should be honored when parsing them.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class PathSupport implements MediaSupport {
    private static final EntityWriter WRITER = new PathWriter();
    private final String name;

    /**
     * Create a named instance.
     *
     * @param name name of this instance
     */
    protected PathSupport(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Create a new media support for writing {@link java.nio.file.Path}.
     *
     * @return a new media support
     */
    public static MediaSupport create() {
        return new PathSupport("path");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "path";
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (Path.class.isAssignableFrom(type.rawType())) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, PathSupport::writer);
        }
        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (Path.class.isAssignableFrom(type.rawType())) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, PathSupport::writer);
        }
        return WriterResponse.unsupported();
    }

    private static <T> EntityWriter<T> writer() {
        return WRITER;
    }

    private static void updateHeaders(Path path, WritableHeaders<?> writableHeaders) {
        if (!writableHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            MediaType mediaType = MediaTypes.detectType(path).orElse(MediaTypes.APPLICATION_OCTET_STREAM);
            writableHeaders.contentType(mediaType);
        }
        if (!writableHeaders.contains(HeaderNames.CONTENT_DISPOSITION)) {
            writableHeaders.set(ContentDisposition.builder()
                                        .filename(String.valueOf(path.getFileName()))
                                        .build());
        }
    }

    private static final class PathWriter implements EntityWriter<Path> {
        @Override
        public void write(GenericType<Path> type,
                          Path object,
                          OutputStream outputStream,
                          Headers requestHeaders,
                          WritableHeaders<?> responseHeaders) {
            write(object, outputStream, responseHeaders);
        }

        @Override
        public void write(GenericType<Path> type,
                          Path object,
                          OutputStream outputStream,
                          WritableHeaders<?> headers) {
            write(object, outputStream, headers);
        }

        @Override
        public boolean supportsInstanceWriter() {
            return true;
        }

        @Override
        public InstanceWriter instanceWriter(GenericType<Path> type, Path object, WritableHeaders<?> requestHeaders) {
            return new PathInstanceWriter(object, requestHeaders);
        }

        @Override
        public InstanceWriter instanceWriter(GenericType<Path> type,
                                             Path object,
                                             Headers requestHeaders,
                                             WritableHeaders<?> responseHeaders) {
            return new PathInstanceWriter(object, responseHeaders);
        }

        private void write(Path toWrite,
                           OutputStream outputStream,
                           WritableHeaders<?> writableHeaders) {

            updateHeaders(toWrite, writableHeaders);

            try (InputStream in = Files.newInputStream(toWrite); outputStream) {
                in.transferTo(outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class PathInstanceWriter implements InstanceWriter {
        private final OptionalLong contentLength;
        private final Path path;

        private PathInstanceWriter(Path path, WritableHeaders<?> writableHeaders) {
            this.path = path;

            OptionalLong discoveredLength;
            try {
                discoveredLength = OptionalLong.of(Files.size(path));
            } catch (IOException e) {
                discoveredLength = OptionalLong.empty();
            }
            this.contentLength = discoveredLength;

            updateHeaders(path, writableHeaders);
        }

        @Override
        public OptionalLong contentLength() {
            return contentLength;
        }

        @Override
        public boolean alwaysInMemory() {
            // we can have huge files that may not fit in memory, always return false here
            return false;
        }

        @Override
        public void write(OutputStream stream) {
            try (InputStream in = Files.newInputStream(path); stream) {
                in.transferTo(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public byte[] instanceBytes() {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

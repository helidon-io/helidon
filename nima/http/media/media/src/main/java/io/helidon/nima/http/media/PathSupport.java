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

package io.helidon.nima.http.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.helidon.common.GenericType;
import io.helidon.common.http.ContentDisposition;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

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

        private void write(Path toWrite,
                           OutputStream outputStream,
                           WritableHeaders<?> writableHeaders) {

            if (!writableHeaders.contains(Http.Header.CONTENT_TYPE)) {
                MediaType mediaType = MediaTypes.detectType(toWrite).orElse(MediaTypes.APPLICATION_OCTET_STREAM);
                writableHeaders.contentType(mediaType);
            }
            if (!writableHeaders.contains(Http.Header.CONTENT_DISPOSITION)) {
                writableHeaders.set(ContentDisposition.builder()
                                            .filename(String.valueOf(toWrite.getFileName()))
                                            .build());
            }

            try (InputStream in = Files.newInputStream(toWrite); outputStream) {
                in.transferTo(outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

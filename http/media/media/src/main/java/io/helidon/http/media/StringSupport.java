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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.GenericType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.WritableHeaders;

/**
 * Media support for strings.
 * This needs to be a proper media support, as encoding should be provided when sending strings,
 * and should be honored when parsing them.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class StringSupport implements MediaSupport {
    private static final Header HEADER_PLAIN_TEXT = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                              HttpMediaTypes.PLAINTEXT_UTF_8.text());
    private static final EntityReader READER = new StringReader();
    private static final EntityWriter WRITER = new StringWriter();
    private final String name;

    // MyStringSupportProvider in SseServerMediaTest extends this class so protected access is required

    /**
     * Creates an instance of media support for strings.
     *
     * @param name name of this instance
     */
    protected StringSupport(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Create a new media support for {@link java.lang.String} processing.
     *
     * @return a new media support
     */
    public static MediaSupport create() {
        return new StringSupport("string");
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (type.equals(GenericType.STRING)) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, StringSupport::reader);
        }
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (type.equals(GenericType.STRING)) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, StringSupport::writer);
        }
        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (type.equals(GenericType.STRING)) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, StringSupport::reader);
        }
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (type.equals(GenericType.STRING)) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, StringSupport::writer);
        }
        return WriterResponse.unsupported();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "string";
    }

    private static <T> EntityReader<T> reader() {
        return READER;
    }

    private static <T> EntityWriter<T> writer() {
        return WRITER;
    }

    private static final class StringWriter implements EntityWriter<String> {

        @Override
        public void write(GenericType<String> type,
                          String object,
                          OutputStream outputStream,
                          Headers requestHeaders,
                          WritableHeaders<?> responseHeaders) {
            write(object, outputStream, responseHeaders);
        }

        @Override
        public void write(GenericType<String> type,
                          String object,
                          OutputStream outputStream,
                          WritableHeaders<?> headers) {
            write(object, outputStream, headers);
        }

        @Override
        public boolean supportsInstanceWriter() {
            return true;
        }

        @Override
        public InstanceWriter instanceWriter(GenericType<String> type, String object, WritableHeaders<?> requestHeaders) {
            return new StringInstanceWriter(object, requestHeaders);
        }

        @Override
        public InstanceWriter instanceWriter(GenericType<String> type,
                                             String object,
                                             Headers requestHeaders,
                                             WritableHeaders<?> responseHeaders) {
            return new StringInstanceWriter(object, responseHeaders);
        }

        private void write(String toWrite,
                           OutputStream outputStream,
                           WritableHeaders<?> writableHeaders) {
            Charset charset;
            if (writableHeaders.contains(HeaderNames.CONTENT_TYPE)) {
                charset = writableHeaders.contentType()
                        .flatMap(HttpMediaType::charset)
                        .map(Charset::forName)
                        .orElse(StandardCharsets.UTF_8);
            } else {
                writableHeaders.set(HEADER_PLAIN_TEXT);
                charset = StandardCharsets.UTF_8;
            }

            try (outputStream) {
                outputStream.write(toWrite.getBytes(charset));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class StringInstanceWriter implements InstanceWriter {
        private final byte[] bytes;

        private StringInstanceWriter(String object, WritableHeaders<?> headers) {
            Charset charset;
            if (headers.contains(HeaderNames.CONTENT_TYPE)) {
                charset = headers.contentType()
                        .flatMap(HttpMediaType::charset)
                        .map(Charset::forName)
                        .orElse(StandardCharsets.UTF_8);
            } else {
                headers.set(HEADER_PLAIN_TEXT);
                charset = StandardCharsets.UTF_8;
            }

            this.bytes = object.getBytes(charset);
        }

        @Override
        public boolean alwaysInMemory() {
            return true;
        }

        @Override
        public OptionalLong contentLength() {
            return OptionalLong.of(bytes.length);
        }

        @Override
        public void write(OutputStream stream) {
            try (stream) {
                stream.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public byte[] instanceBytes() {
            return bytes;
        }
    }

    private static final class StringReader implements EntityReader<String> {
        @Override
        public String read(GenericType<String> type, InputStream stream, Headers headers) {
            return read(stream, headers.contentType());
        }

        @Override
        public String read(GenericType<String> type,
                           InputStream stream,
                           Headers requestHeaders,
                           Headers responseHeaders) {
            return read(stream, responseHeaders.contentType());
        }

        private String read(InputStream stream, Optional<HttpMediaType> contentType) {
            Charset charset = contentType
                    .flatMap(HttpMediaType::charset)
                    .map(Charset::forName)
                    .orElse(StandardCharsets.UTF_8);

            try (stream) {
                return new String(stream.readAllBytes(), charset);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

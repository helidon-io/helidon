/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.webserver.basics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaSupport;

/**
 * Reader for the custom media type.
 */
public class NameSupport implements MediaSupport {

    static final HttpMediaType APP_NAME = HttpMediaType.create("application/name");

    private NameSupport() {
    }

    /**
     * Create a new instance.
     *
     * @return new instance
     */
    public static NameSupport create() {
        return new NameSupport();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers headers) {
        if (!type.rawType().equals(Name.class)
            || !headers.contentType().map(APP_NAME::equals).orElse(false)) {
            return ReaderResponse.unsupported();
        }
        return (ReaderResponse<T>) new ReaderResponse<>(SupportLevel.SUPPORTED, () -> new EntityReader<Name>() {
            @Override
            public Name read(GenericType<Name> type, InputStream stream, Headers headers) {
                return read(stream, headers);
            }

            @Override
            public Name read(GenericType<Name> type,
                             InputStream stream,
                             Headers requestHeaders,
                             Headers responseHeaders) {
                return read(stream, responseHeaders);
            }

            private Name read(InputStream stream, Headers headers) {
                Charset charset = headers.contentType()
                        .flatMap(HttpMediaType::charset)
                        .map(Charset::forName)
                        .orElse(StandardCharsets.UTF_8);

                try (stream) {
                    return new Name(new String(stream.readAllBytes(), charset));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    @Override
    public String name() {
        return "name";
    }

    @Override
    public String type() {
        return "name";
    }
}





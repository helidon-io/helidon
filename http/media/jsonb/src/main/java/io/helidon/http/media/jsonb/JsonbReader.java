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

package io.helidon.http.media.jsonb;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.EntityReader;

import jakarta.json.bind.Jsonb;

class JsonbReader<T> implements EntityReader<T> {
    private final Jsonb jsonb;

    JsonbReader(Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        return read(type, stream, contentTypeCharset(headers));
    }

    @Override
    public T read(GenericType<T> type,
                  InputStream stream,
                  Headers requestHeaders,
                  Headers responseHeaders) {
        return read(type, stream, contentTypeCharset(responseHeaders));
    }

    private T read(GenericType<T> type, InputStream in, Charset charset) {

        try (Reader r = new InputStreamReader(in, charset)) {
            return jsonb.fromJson(r, type.type());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Charset contentTypeCharset(Headers headers) {
        return headers.contentType()
                .flatMap(HttpMediaType::charset)
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8);
    }
}

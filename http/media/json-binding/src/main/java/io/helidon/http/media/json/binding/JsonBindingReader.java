/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.json.binding;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.EntityReader;
import io.helidon.json.binding.JsonBinding;

class JsonBindingReader<T> implements EntityReader<T> {
    private final JsonBinding jsonBinding;

    JsonBindingReader(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        Optional<InputStreamReader> reader = contentTypeCharset(headers)
                .map(charset -> new InputStreamReader(stream, charset));
        if (reader.isPresent()) {
            return read(type, reader.get());
        }
        return read(type, stream);
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
        Optional<InputStreamReader> reader = contentTypeCharset(responseHeaders)
                .map(charset -> new InputStreamReader(stream, charset));
        if (reader.isPresent()) {
            return read(type, reader.get());
        }
        return read(type, stream);
    }

    private T read(GenericType<T> type, InputStream in) {
        try (in) {
            return jsonBinding.deserialize(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private T read(GenericType<T> type, Reader reader) {
        try (reader) {
            return jsonBinding.deserialize(reader, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Charset> contentTypeCharset(Headers headers) {
        return headers.contentType()
                .flatMap(HttpMediaType::charset)
                .map(Charset::forName);
    }
}

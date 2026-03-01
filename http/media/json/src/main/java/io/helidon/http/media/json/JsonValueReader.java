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

package io.helidon.http.media.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.media.EntityReaderBase;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;

class JsonValueReader<T extends JsonValue> extends EntityReaderBase<T> {
    JsonValueReader() {
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        var charset = findContentTypeCharset(headers);
        return charset.map(it -> read(type, stream, it))
                .orElseGet(() -> read(type, stream));
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
        var charset = findContentTypeCharset(responseHeaders);
        return charset.map(it -> read(type, stream, it))
                .orElseGet(() -> read(type, stream));
    }

    private T read(GenericType<T> type, InputStream in) {
        return type.cast(JsonParser.create(in)
                                 .readJsonValue());
    }

    private T read(GenericType<T> type, InputStream in, Charset charset) {
        try (Reader r = new InputStreamReader(in, charset)) {
            return type.cast(JsonParser.create(r)
                                     .readJsonValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.media.jsonp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.nima.http.media.EntityReader;

import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonStructure;

class JsonpReader<T extends JsonStructure> implements EntityReader<T> {
    private final JsonReaderFactory readerFactory;

    JsonpReader(JsonReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        return read(type, stream);
    }

    @Override
    public T read(GenericType<T> type,
                  InputStream stream,
                  Headers requestHeaders,
                  Headers responseHeaders) {
        return read(type, stream);
    }

    private T read(GenericType<T> type, InputStream in) {
        try (in) {
            return type.cast(readerFactory.createReader(in)
                                     .read());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

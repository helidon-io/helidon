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

package io.helidon.http.media.json.smile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.media.EntityReaderBase;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileParser;

class SmileReader<T> extends EntityReaderBase<T> {

    private final JsonBinding jsonBinding;

    SmileReader(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        return read(type, stream);
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
        return read(type, stream);
    }

    private T read(GenericType<T> type, InputStream in) {
        try (in) {
            return jsonBinding.deserialize(SmileParser.create(in), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

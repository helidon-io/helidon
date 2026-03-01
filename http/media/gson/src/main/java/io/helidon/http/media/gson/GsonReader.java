/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.http.media.gson;

import java.io.InputStream;
import java.io.InputStreamReader;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.media.EntityReaderBase;

import com.google.gson.Gson;

class GsonReader<T> extends EntityReaderBase<T> {
    private final Gson gson;

    GsonReader(Gson gson) {
        this.gson = gson;
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers headers) {
        return gson.fromJson(new InputStreamReader(stream, contentTypeCharset(headers)), type.type());
    }

    @Override
    public T read(GenericType<T> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
        return gson.fromJson(new InputStreamReader(stream, contentTypeCharset(responseHeaders)), type.type());
    }
}

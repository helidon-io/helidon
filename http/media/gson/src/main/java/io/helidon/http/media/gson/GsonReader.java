/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.EntityReader;

import com.google.gson.Gson;

class GsonReader<T> implements EntityReader<T> {
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

    private Charset contentTypeCharset(Headers headers) {
        return headers.contentType()
                .flatMap(HttpMediaType::charset)
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8);
    }
}

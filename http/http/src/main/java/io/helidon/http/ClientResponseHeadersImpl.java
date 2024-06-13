/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.media.type.ParserMode;

class ClientResponseHeadersImpl implements ClientResponseHeaders {
    private final Headers headers;
    private final ParserMode parserMode;

    ClientResponseHeadersImpl(Headers headers, ParserMode parserMode) {
        this.headers = headers;
        this.parserMode = parserMode;
    }

    @Override
    public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
        return headers.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(HeaderName name) {
        return headers.contains(name);
    }

    @Override
    public boolean contains(Header headerWithValue) {
        return headers.contains(headerWithValue);
    }

    @Override
    public Header get(HeaderName name) {
        return headers.get(name);
    }

    @Override
    public Optional<HttpMediaType> contentType() {
        if (parserMode == ParserMode.RELAXED) {
            return contains(HeaderNameEnum.CONTENT_TYPE)
                    ? Optional.of(HttpMediaType.create(get(HeaderNameEnum.CONTENT_TYPE).get(), parserMode))
                    : Optional.empty();
        }
        return headers.contentType();
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public Iterator<Header> iterator() {
        return headers.iterator();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return headers.acceptedTypes();
    }

    @Override
    public String toString() {
        return headers.toString();
    }
}

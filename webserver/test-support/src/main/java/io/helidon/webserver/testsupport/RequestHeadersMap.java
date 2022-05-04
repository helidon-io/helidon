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

package io.helidon.webserver.testsupport;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.webserver.RequestHeaders;

class RequestHeadersMap implements RequestHeaders {
    private final HeadersWritable<?> delegate;

    RequestHeadersMap(Map<String, List<String>> headers) {
        this.delegate = HeadersWritable.create();
        headers.forEach((key, value) -> delegate.set(Http.Header.create(key), value));
    }

    @Override
    @Deprecated(forRemoval = true)
    public List<String> all(String headerName) {
        return delegate.all(headerName);
    }

    @Override
    public List<String> all(Http.HeaderName name, Supplier<List<String>> defaultSupplier) {
        return delegate.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(Http.HeaderName name) {
        return delegate.contains(name);
    }

    @Override
    public boolean contains(Http.HeaderValue value) {
        return delegate.contains(value);
    }

    @Override
    public Http.HeaderValue get(Http.HeaderName name) {
        return delegate.get(name);
    }

    @Override
    public Optional<String> value(Http.HeaderName headerName) {
        return delegate.value(headerName);
    }

    @Override
    public Optional<String> first(Http.HeaderName headerName) {
        return delegate.first(headerName);
    }

    @Override
    public List<String> values(Http.HeaderName headerName) {
        return delegate.values(headerName);
    }

    @Override
    public OptionalLong contentLength() {
        return delegate.contentLength();
    }

    @Override
    public Optional<HttpMediaType> contentType() {
        return delegate.contentType();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return delegate.acceptedTypes();
    }

    @Override
    public boolean isAccepted(MediaType mediaType) {
        return delegate.isAccepted(mediaType);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Map<String, List<String>> toMap() {
        return delegate.toMap();
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return delegate.iterator();
    }

    @Override
    public void forEach(Consumer<? super Http.HeaderValue> action) {
        delegate.forEach(action);
    }

    @Override
    public Spliterator<Http.HeaderValue> spliterator() {
        return delegate.spliterator();
    }
}

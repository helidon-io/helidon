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
package io.helidon.http;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaType;

/**
 * A response headers wrapper class with rollback capabilities.
 */
public class ServerResponseHeadersWrapper implements ServerResponseHeaders {

    private final ServerResponseHeaders delegate;
    private Function<ServerResponseHeaders, ServerResponseHeaders> rollbackFunction = Function.identity();

    /**
     * Creates a new headers wrapper with rollback capabilities.
     *
     * @param delegate the delegate
     * @return the wrapper
     */
    public static ServerResponseHeadersWrapper create(ServerResponseHeaders delegate) {
        return new ServerResponseHeadersWrapper(delegate);
    }

    private ServerResponseHeadersWrapper(ServerResponseHeaders delegate) {
        this.delegate = delegate;
    }

    /**
     * Rollback the headers to the initial state when this instance was created.
     *
     * @return rolled back headers
     */
    public ServerResponseHeaders rollback() {
        return rollbackFunction.apply(delegate);
    }

    @Override
    public List<HttpMediaType> acceptPatches() {
        return delegate.acceptPatches();
    }

    @Override
    public Optional<URI> location() {
        return delegate.location();
    }

    @Override
    public Optional<ZonedDateTime> lastModified() {
        return delegate.lastModified();
    }

    @Override
    public Optional<ZonedDateTime> expires() {
        return delegate.expires();
    }

    @Override
    public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
        return delegate.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(HeaderName name) {
        return delegate.contains(name);
    }

    @Override
    public boolean contains(Header value) {
        return delegate.contains(value);
    }

    @Override
    public Header get(HeaderName name) {
        return delegate.get(name);
    }

    @Override
    public Optional<String> value(HeaderName headerName) {
        return delegate.value(headerName);
    }

    @Override
    public Optional<String> first(HeaderName headerName) {
        return delegate.first(headerName);
    }

    @Override
    public List<String> values(HeaderName headerName) {
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
    public void forEach(Consumer<? super Header> action) {
        delegate.forEach(action);
    }

    @Override
    public Spliterator<Header> spliterator() {
        return delegate.spliterator();
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
    public Map<String, List<String>> toMap() {
        return delegate.toMap();
    }

    @Override
    public Stream<Header> stream() {
        return delegate.stream();
    }

    @Override
    public Iterator<Header> iterator() {
        return delegate.iterator();
    }

    @Override
    public ServerResponseHeaders location(URI location) {
        return delegate.location(location);
    }

    @Override
    public ServerResponseHeaders expires(ZonedDateTime dateTime) {
        return delegate.expires(dateTime);
    }

    @Override
    public ServerResponseHeaders expires(Instant dateTime) {
        return delegate.expires(dateTime);
    }

    @Override
    public ServerResponseHeaders lastModified(Instant modified) {
        return delegate.lastModified(modified);
    }

    @Override
    public ServerResponseHeaders lastModified(ZonedDateTime modified) {
        return delegate.lastModified(modified);
    }

    @Override
    public ServerResponseHeaders contentType(MediaType contentType) {
        return delegate.contentType(contentType);
    }

    @Override
    public ServerResponseHeaders from(Headers headers) {
        return delegate.from(headers);
    }

    // -- write methods -------------------------------------------------------

    @Override
    public ServerResponseHeaders setIfAbsent(Header header) {
        HeaderName headerName = header.headerName();
        if (!delegate.contains(headerName)) {
            return set(header);
        }
        return this;
    }

    @Override
    public ServerResponseHeaders add(Header header) {
        HeaderName headerName = header.headerName();
        var nestedRollback = rollbackFunction;
        if (delegate.contains(headerName)) {
            List<String> allValues = new ArrayList<>(delegate.get(headerName).allValues());
            rollbackFunction = headers -> {
                headers.set(headerName, allValues);
                return nestedRollback.apply(headers);
            };
        } else {
            rollbackFunction = headers -> {
                headers.remove(headerName);
                return nestedRollback.apply(headers);
            };
        }
        return delegate.add(header);
    }

    @Override
    public ServerResponseHeaders remove(HeaderName name) {
        if (delegate.contains(name)) {
            List<String> allValues = new ArrayList<>(delegate.get(name).allValues());
            var nestedRollback = rollbackFunction;
            rollbackFunction = headers -> {
                headers.set(name, allValues);
                return nestedRollback.apply(headers);
            };
            return delegate.remove(name);
        }
        return this;
    }

    @Override
    public ServerResponseHeaders remove(HeaderName name, Consumer<Header> removedConsumer) {
        if (delegate.contains(name)) {
            List<String> allValues = new ArrayList<>(delegate.get(name).allValues());
            var nestedRollback = rollbackFunction;
            rollbackFunction = headers -> {
                headers.set(name, allValues);
                return nestedRollback.apply(headers);
            };
            return delegate.remove(name, removedConsumer);
        }
        return this;
    }

    @Override
    public ServerResponseHeaders set(Header header) {
        HeaderName headerName = header.headerName();
        if (delegate.contains(headerName)) {
            List<String> allValues = new ArrayList<>(delegate.get(headerName).allValues());
            var nestedRollback = rollbackFunction;
            rollbackFunction = headers -> {
                headers.set(headerName, allValues);
                return nestedRollback.apply(headers);
            };
        } else {
            var nestedRollback = rollbackFunction;
            rollbackFunction = headers -> {
                headers.remove(headerName);
                return nestedRollback.apply(headers);
            };
        }
        return delegate.set(header);
    }

    @Override
    public ServerResponseHeaders clear() {
        if (delegate.size() > 0) {
            ServerResponseHeaders cloned = ServerResponseHeaders.create(delegate);
            var nestedRollback = rollbackFunction;
            rollbackFunction = headers -> {
                for (Header h : cloned) {
                    headers.set(h.headerName(), h.values());
                }
                return nestedRollback.apply(headers);
            };
            return delegate.clear();
        }
        return this;
    }

    @Override
    public ServerResponseHeaders addCookie(SetCookie cookie) {
        add(HeaderValues.create(HeaderNames.SET_COOKIE, cookie.toString()));
        return this;
    }

    @Override
    public ServerResponseHeaders clearCookie(SetCookie cookie) {
        ServerResponseHeadersImpl.clearCookie(this, cookie, cookie::equals);
        return this;
    }

    @Override
    public ServerResponseHeaders clearCookie(String name) {
        ServerResponseHeadersImpl.clearCookie(this,
                                              SetCookie.builder(name, "").build(),
                                              c -> c.name().equals(name));
        return this;
    }
}

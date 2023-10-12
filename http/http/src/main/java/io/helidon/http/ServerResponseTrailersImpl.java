/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.function.Consumer;
import java.util.function.Supplier;

class ServerResponseTrailersImpl implements ServerResponseTrailers {
    private final WritableHeaders<?> delegate;

    ServerResponseTrailersImpl(WritableHeaders<?> delegate) {
        this.delegate = delegate;
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
    public int size() {
        return delegate.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return delegate.acceptedTypes();
    }

    @Override
    public ServerResponseTrailers setIfAbsent(Header header) {
        delegate.setIfAbsent(header);
        return this;
    }

    @Override
    public ServerResponseTrailers add(Header header) {
        delegate.add(header);
        return this;
    }

    @Override
    public ServerResponseTrailers remove(HeaderName name) {
        delegate.remove(name);
        return this;
    }

    @Override
    public ServerResponseTrailers remove(HeaderName name, Consumer<Header> removedConsumer) {
        delegate.remove(name, removedConsumer);
        return this;
    }

    @Override
    public ServerResponseTrailers set(Header header) {
        delegate.set(header);
        return this;
    }

    @Override
    public ServerResponseTrailers clear() {
        delegate.clear();
        return this;
    }

    @Override
    public ServerResponseTrailers from(Headers headers) {
        delegate.from(headers);
        return this;
    }

    @Override
    public Iterator<Header> iterator() {
        return delegate.iterator();
    }
}

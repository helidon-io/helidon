/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client request headers.
 */
class ClientRequestHeadersImpl implements ClientRequestHeaders {
    private final WritableHeaders<?> delegate;

    private List<HttpMediaType> mediaTypes;

    ClientRequestHeadersImpl(WritableHeaders<?> delegate) {
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
    public boolean contains(Header headerWithValue) {
        return delegate.contains(headerWithValue);
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
        if (mediaTypes == null) {
            if (delegate.contains(HeaderNames.ACCEPT)) {
                List<String> accepts = delegate.get(HeaderNames.ACCEPT).allValues(true);

                List<HttpMediaType> mediaTypes = new ArrayList<>(accepts.size());
                for (String accept : accepts) {
                    mediaTypes.add(HttpMediaType.create(accept));
                }
                Collections.sort(mediaTypes);
                this.mediaTypes = List.copyOf(mediaTypes);
            } else {
                this.mediaTypes = List.of();
            }
        }

        return mediaTypes;
    }

    @Override
    public ClientRequestHeaders setIfAbsent(Header header) {
        delegate.setIfAbsent(header);
        return this;
    }

    @Override
    public ClientRequestHeaders add(Header header) {
        delegate.add(header);
        return this;
    }

    @Override
    public ClientRequestHeaders remove(HeaderName name) {
        delegate.remove(name);
        return this;
    }

    @Override
    public ClientRequestHeaders remove(HeaderName name, Consumer<Header> removedConsumer) {
        delegate.remove(name, removedConsumer);
        return this;
    }

    @Override
    public ClientRequestHeaders set(Header header) {
        delegate.set(header);
        return this;
    }

    @Override
    public Iterator<Header> iterator() {
        return delegate.iterator();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public ClientRequestHeaders clear() {
        delegate.clear();
        return this;
    }

    @Override
    public ClientRequestHeaders from(Headers headers) {
        headers.forEach(this::set);
        return this;
    }
}

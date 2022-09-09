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

package io.helidon.reactive.webserver;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.common.http.WritableHeaders;

import io.netty.handler.codec.http.HttpHeaders;

class NettyRequestHeaders implements RequestHeaders {
    private final ServerRequestHeaders delegate;

    NettyRequestHeaders(HttpHeaders nettyHeaders) {
        WritableHeaders<?> hw = WritableHeaders.create();
        for (String name : nettyHeaders.names()) {
            hw.set(Http.Header.create(Http.Header.create(name), nettyHeaders.getAll(name)));
        }
        this.delegate = ServerRequestHeaders.create(hw);
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
    public int size() {
        return delegate.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return delegate.acceptedTypes();
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return delegate.iterator();
    }
}

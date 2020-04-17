/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.webserver.ServerRequest;

/**
 * Helidon SE implementation of {@link CorsSupportBase.RequestAdapter}.
 */
class RequestAdapterSe implements CorsSupportBase.RequestAdapter<ServerRequest> {

    private final ServerRequest request;

    RequestAdapterSe(ServerRequest request) {
        this.request = request;
    }

    @Override
    public String path() {
        return request.path().toString();
    }

    @Override
    public Optional<String> firstHeader(String key) {
        return request.headers().first(key);
    }

    @Override
    public boolean headerContainsKey(String key) {
        return firstHeader(key).isPresent();
    }

    @Override
    public List<String> allHeaders(String key) {
        return request.headers().all(key);
    }

    @Override
    public String method() {
        return request.method().name();
    }

    @Override
    public void next() {
        request.next();
    }

    @Override
    public ServerRequest request() {
        return request;
    }

    @Override
    public String toString() {
        return String.format("RequestAdapterSe{path=%s, method=%s, headers=%s}", path(), method(), request.headers().toMap());
    }
}

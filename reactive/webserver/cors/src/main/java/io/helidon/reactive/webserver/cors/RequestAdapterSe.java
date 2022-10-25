/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.reactive.webserver.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.cors.CorsRequestAdapter;
import io.helidon.reactive.webserver.ServerRequest;

/**
 * Helidon SE implementation of {@link io.helidon.cors.CorsRequestAdapter}.
 */
class RequestAdapterSe implements CorsRequestAdapter<ServerRequest> {

    private final ServerRequest request;

    RequestAdapterSe(ServerRequest request) {
        this.request = request;
    }

    @Override
    public String path() {
        return request.path().toString();
    }

    @Override
    public Optional<String> firstHeader(Http.HeaderName key) {
        return request.headers().value(key);
    }

    @Override
    public boolean headerContainsKey(Http.HeaderName key) {
        return request.headers().contains(key);
    }

    @Override
    public List<String> allHeaders(Http.HeaderName key) {
        return request.headers().all(key, List::of);
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
    public String authority() {
        return firstHeader(Http.Header.HOST).orElse("localhost");
    }

    @Override
    public String toString() {
        return String.format("RequestAdapterSe{path=%s, method=%s, headers=%s}", path(), method(), request.headers());
    }
}

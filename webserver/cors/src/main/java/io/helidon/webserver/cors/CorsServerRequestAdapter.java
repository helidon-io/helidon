/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.common.uri.UriInfo;
import io.helidon.cors.CorsRequestAdapter;
import io.helidon.http.HeaderName;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Implementation of {@link CorsRequestAdapter} that adapts {@link ServerRequest}.
 */
class CorsServerRequestAdapter implements CorsRequestAdapter<ServerRequest> {

    private final ServerRequest request;
    private final ServerResponse response;
    private final ServerRequestHeaders headers;

    CorsServerRequestAdapter(ServerRequest req, ServerResponse res) {
        this.request = req;
        this.response = res;
        this.headers = req.headers();
    }

    @Override
    public UriInfo requestedUri() {
        return request.requestedUri();
    }

    @Override
    public String path() {
        return request.path().path();
    }

    @Override
    public Optional<String> firstHeader(HeaderName key) {
        if (headers.contains(key)) {
            return Optional.of(headers.get(key).get());
        }
        return Optional.empty();
    }

    @Override
    public boolean headerContainsKey(HeaderName key) {
        return headers.contains(key);
    }

    @Override
    public List<String> allHeaders(HeaderName key) {
        return headers.all(key, List::of);
    }

    @Override
    public String method() {
        return request.prologue().method().text();
    }

    @Override
    public boolean isSecure() {
        return request.isSecure();
    }

    @Override
    public void next() {
        response.next();
    }

    @Override
    public ServerRequest request() {
        return request;
    }

    @Override
    public String toString() {
        return String.format("RequestAdapterSe{path=%s, method=%s, headers=%s}", path(), method(), request.headers());
    }
}

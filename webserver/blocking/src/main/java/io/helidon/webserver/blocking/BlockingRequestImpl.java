/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.blocking;

import java.net.URI;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.Parameters;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ServerRequest;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Blocking request implementation.
 */
class BlockingRequestImpl implements BlockingRequest {
    private final ServerRequest req;

    BlockingRequestImpl(ServerRequest req) {
        this.req = req;
    }

    static BlockingRequest create(ServerRequest req) {
        return new BlockingRequestImpl(req);
    }

    @Override
    public BlockingContent content() {
        return BlockingContentImpl.create(req.content());
    }

    @Override
    public HttpRequest.Path path() {
        return req.path();
    }

    @Override
    public Context context() {
        return req.context();
    }

    @Override
    public String localAddress() {
        return req.localAddress();
    }

    @Override
    public int localPort() {
        return req.localPort();
    }

    @Override
    public String remoteAddress() {
        return req.remoteAddress();
    }

    @Override
    public int remotePort() {
        return req.remotePort();
    }

    @Override
    public boolean isSecure() {
        return req.isSecure();
    }

    @Override
    public RequestHeaders headers() {
        return req.headers();
    }

    @Override
    public long requestId() {
        return req.requestId();
    }

    @Override
    public Optional<SpanContext> spanContext() {
        return req.spanContext();
    }

    @Override
    public Tracer tracer() {
        return req.tracer();
    }

    @Override
    public Http.RequestMethod method() {
        return req.method();
    }

    @Override
    public Http.Version version() {
        return req.version();
    }

    @Override
    public URI uri() {
        return req.uri();
    }

    @Override
    public String query() {
        return req.query();
    }

    @Override
    public Parameters queryParams() {
        return req.queryParams();
    }

    @Override
    public String fragment() {
        return req.fragment();
    }
}

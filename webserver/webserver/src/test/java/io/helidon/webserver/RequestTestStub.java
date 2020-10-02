/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.URI;
import java.util.Optional;

import io.helidon.common.reactive.Single;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The RequestTestStub.
 */
public class RequestTestStub extends Request {

    private final Span span;

    RequestTestStub() {
        this(bareRequestMock(), mock(WebServer.class));
    }

    RequestTestStub(BareRequest req, WebServer webServer) {
        this(req, webServer, GlobalTracer.get().buildSpan("unit-test-request").start());
    }

    @Override
    public Tracer tracer() {
        return GlobalTracer.get();
    }

    RequestTestStub(BareRequest req, WebServer webServer, Span span) {
        super(req, webServer, new HashRequestHeaders(req.headers()));
        this.span = span == null ? mock(Span.class) : span;
    }

    private static BareRequest bareRequestMock() {
        BareRequest bareRequestMock = mock(BareRequest.class);
        doReturn(URI.create("http://0.0.0.0:1234")).when(bareRequestMock).uri();
        doReturn(Single.empty()).when(bareRequestMock).bodyPublisher();
        return bareRequestMock;
    }

    @Override
    public Optional<SpanContext> spanContext() {
        return Optional.ofNullable(span.context());
    }

    @Override
    public void next() {

    }

    @Override
    public void next(Throwable t) {

    }

    @Override
    public ServerRequest.Path path() {
        return null;
    }

    @Override
    public long requestId() {
        return 0;
    }
}

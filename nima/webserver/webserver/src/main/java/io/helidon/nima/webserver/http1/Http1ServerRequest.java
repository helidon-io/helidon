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

package io.helidon.nima.webserver.http1;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.HttpRequestBase;
import io.helidon.nima.webserver.http.RoutedPath;
import io.helidon.nima.webserver.http.RoutingRequest;

/**
 * Http 1 server request base.
 */
abstract class Http1ServerRequest extends HttpRequestBase implements RoutingRequest {
    private final HeadersServerRequest headers;
    private final ConnectionContext ctx;
    private final HttpPrologue prologue;
    private final int requestId;

    private RoutedPath path;
    private HeadersWritable<?> writable;

    private HttpPrologue newPrologue;
    // cached authority
    private String authority;

    Http1ServerRequest(ConnectionContext ctx,
                       HttpPrologue prologue,
                       Headers headers,
                       int requestId) {
        super(ctx);
        this.ctx = ctx;
        this.prologue = prologue;
        this.headers = HeadersServerRequest.create(headers);
        this.requestId = requestId;
        this.newPrologue = prologue;
    }

    /**
     * Create a new request without an entity.
     *
     * @param prologue
     * @param headers
     * @return
     */
    static Http1ServerRequest create(ConnectionContext ctx,
                                     HttpPrologue prologue,
                                     Headers headers,
                                     int requestId) {
        return new Http1ServerRequestNoEntity(ctx, prologue, headers, requestId);
    }

    /**
     * Create a new request with an entity.
     *
     * @param prologue
     * @param headers
     * @param requestContext
     * @param entitySupplier
     * @return
     */
    static Http1ServerRequest create(ConnectionContext ctx,
                                     HttpPrologue prologue,
                                     HeadersServerRequest headers,
                                     ContentDecoder decoder,
                                     int requestContext,
                                     CountDownLatch entityReadLatch,
                                     Supplier<BufferData> entitySupplier) {
        return new Http1ServerRequestWithEntity(ctx,
                                                prologue,
                                                headers,
                                                decoder,
                                                requestContext,
                                                entityReadLatch,
                                                entitySupplier);
    }

    @Override
    public boolean isSecure() {
        return ctx.isSecure();
    }

    @Override
    public RoutedPath path() {
        return path;
    }

    @Override
    public String socketId() {
        return ctx.childSocketId();
    }

    @Override
    public String serverSocketId() {
        return ctx.socketId();
    }

    @Override
    public HttpPrologue prologue() {
        return newPrologue;
    }

    @Override
    public HeadersServerRequest headers() {
        return writable == null ? headers : HeadersServerRequest.create(writable);
    }

    @Override
    public UriQuery query() {
        return prologue().query();
    }

    @Override
    public PeerInfo remotePeer() {
        return ctx.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return ctx.localPeer();
    }

    @Override
    public void header(Http.HeaderValue header) {
        if (writable == null) {
            writable = HeadersWritable.create(headers);
        }
        writable.set(header);
    }

    @Override
    public Http1ServerRequest path(RoutedPath routedPath) {
        this.path = routedPath;
        return this;
    }

    @Override
    public Http1ServerRequest prologue(HttpPrologue newPrologue) {
        this.newPrologue = newPrologue;
        return this;
    }

    @Override
    protected String host() {
        return headers().get(Http.Header.HOST).value();
    }
}

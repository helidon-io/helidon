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

package io.helidon.nima.http2.webserver;

import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.RoutedPath;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.ListenerContext;
import io.helidon.nima.webserver.http.HttpSecurity;
import io.helidon.nima.webserver.http.RoutingRequest;

/**
 * HTTP/2 server request.
 */
class Http2ServerRequest implements RoutingRequest {
    private static final Runnable NO_OP_RUNNABLE = () -> {
    };
    private final Http2Headers http2Headers;
    private final ServerRequestHeaders headers;
    private final ConnectionContext ctx;
    private final HttpPrologue originalPrologue;
    private final int requestId;
    private final String authority;
    private final LazyValue<Http2ServerRequestEntity> entity;
    private final HttpSecurity security;
    private final LazyValue<UriInfo> uriInfo = LazyValue.create(this::createUriInfo);

    private HttpPrologue prologue;
    private RoutedPath path;
    private WritableHeaders<?> writable;
    private Context context;
    // preparation for continue support in HTTP/2
    private boolean continueSent;

    Http2ServerRequest(ConnectionContext ctx,
                       HttpSecurity security,
                       HttpPrologue prologue,
                       Http2Headers headers,
                       ContentDecoder decoder,
                       int requestId,
                       Supplier<BufferData> entitySupplier) {
        this.ctx = ctx;
        this.security = security;
        this.originalPrologue = prologue;
        this.http2Headers = headers;
        this.headers = ServerRequestHeaders.create(headers.httpHeaders());
        this.requestId = requestId;
        this.authority = headers.authority();

        this.entity = LazyValue.create(() -> Http2ServerRequestEntity.create(decoder,
                                                                             it -> entitySupplier.get(),
                                                                             NO_OP_RUNNABLE,
                                                                             this.headers,
                                                                             ctx.listenerContext().mediaContext()));
    }

    static Http2ServerRequest create(ConnectionContext ctx,
                                     HttpSecurity security,
                                     HttpPrologue httpPrologue,
                                     Http2Headers headers,
                                     ContentDecoder decoder,
                                     int streamId,
                                     Supplier<BufferData> entitySupplier) {
        return new Http2ServerRequest(ctx, security, httpPrologue, headers, decoder, streamId, entitySupplier);
    }

    @Override
    public void reset() {
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
    public ReadableEntity content() {
        return entity.get();
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
        return prologue == null ? originalPrologue : prologue;
    }

    @Override
    public ServerRequestHeaders headers() {
        return headers;
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
    public String authority() {
        return authority;
    }

    @Override
    public void header(Http.Header header) {
        if (writable == null) {
            writable = WritableHeaders.create(headers);
        }
        writable.set(header);
    }

    @Override
    public int id() {
        return requestId;
    }

    @Override
    public Http2ServerRequest path(RoutedPath routedPath) {
        this.path = routedPath;
        return this;
    }

    @Override
    public RoutingRequest prologue(HttpPrologue newPrologue) {
        this.prologue = newPrologue;
        return this;
    }

    @Override
    public ListenerContext listenerContext() {
        return ctx.listenerContext();
    }

    @Override
    public Context context() {
        if (context == null) {
            context = Contexts.context().orElseGet(() -> Context.builder()
                    .parent(ctx.listenerContext().context())
                    .id("[" + serverSocketId() + " " + socketId() + "] http/1.1: " + requestId)
                    .build());
        }
        return context;
    }

    @Override
    public HttpSecurity security() {
        return security;
    }

    @Override
    public UriInfo requestedUri() {
        return uriInfo.get();
    }

    @Override
    public boolean continueSent() {
        return continueSent;
    }

    private UriInfo createUriInfo() {
        return ctx.listenerContext().config().requestedUriDiscoveryContext().uriInfo(remotePeer().address().toString(),
                                                                                     localPeer().address().toString(),
                                                                                     path.path(),
                                                                                     headers,
                                                                                     query(),
                                                                                     isSecure());
    }
}

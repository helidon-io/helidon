/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.RoutingRequest;

/**
 * HTTP/2 server request.
 */
class Http2ServerRequest implements RoutingRequest {
    private static final RequestedUriDiscoveryContext DEFAULT_REQUESTED_URI_DISCOVERY_CONTEXT =
            RequestedUriDiscoveryContext.builder()
                    .build();

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
    private UnaryOperator<InputStream> streamFilter = UnaryOperator.identity();
    private String matchingPattern;

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

        this.entity = LazyValue.create(() -> Http2ServerRequestEntity.create(streamFilter,
                                                                             decoder,
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
    public void header(Header header) {
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
    public RoutingRequest matchingPattern(String matchingPattern) {
        this.matchingPattern = matchingPattern;
        return this;
    }

    @Override
    public Optional<String> matchingPattern() {
        return Optional.of(matchingPattern);
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

    @Override
    public void streamFilter(UnaryOperator<InputStream> filterFunction) {
        Objects.requireNonNull(filterFunction);
        UnaryOperator<InputStream> current = this.streamFilter;
        this.streamFilter = it -> filterFunction.apply(current.apply(it));
    }

    @Override
    public Optional<ProxyProtocolData> proxyProtocolData() {
        return ctx.proxyProtocolData();
    }

    private UriInfo createUriInfo() {
        return ctx.listenerContext().config().requestedUriDiscoveryContext()
                .orElse(DEFAULT_REQUESTED_URI_DISCOVERY_CONTEXT)
                .uriInfo(remotePeer().address(),
                         localPeer().address(),
                         path.absolute().path(),
                         headers,
                         query(),
                         isSecure());
    }
}

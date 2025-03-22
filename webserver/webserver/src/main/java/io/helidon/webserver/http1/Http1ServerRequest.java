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

package io.helidon.webserver.http1;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.RoutingRequest;

/**
 * Http 1 server request base.
 */
abstract class Http1ServerRequest implements RoutingRequest {

    private static final RequestedUriDiscoveryContext DEFAULT_REQUESTED_URI_DISCOVERY_CONTEXT =
            RequestedUriDiscoveryContext.builder()
            .build();

    private final ServerRequestHeaders headers;
    private final ConnectionContext ctx;
    private final HttpSecurity security;
    private final int requestId;
    private final LazyValue<UriInfo> uriInfo = LazyValue.create(this::createUriInfo);

    private RoutedPath path;
    private WritableHeaders<?> writable;

    private HttpPrologue prologue;
    private Context context;

    Http1ServerRequest(ConnectionContext ctx,
                       HttpSecurity security,
                       HttpPrologue prologue,
                       Headers headers,
                       int requestId) {
        this.ctx = ctx;
        this.security = security;
        this.headers = ServerRequestHeaders.create(headers);
        this.requestId = requestId;
        this.prologue = prologue;
    }

    /*
     * Create a new request without an entity.
     */
    static Http1ServerRequest create(ConnectionContext ctx,
                                     HttpSecurity security,
                                     HttpPrologue prologue,
                                     Headers headers,
                                     int requestId) {
        return new Http1ServerRequestNoEntity(ctx, security, prologue, headers, requestId);
    }

    /*
     * Create a new request with an entity.
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // all parameters are always needed, record would not bring any benefit
    static Http1ServerRequest create(ConnectionContext ctx,
                                     Http1Connection connection,
                                     Http1Config http1Config,
                                     HttpSecurity security,
                                     HttpPrologue prologue,
                                     ServerRequestHeaders headers,
                                     ContentDecoder decoder,
                                     int requestId,
                                     boolean expectContinue,
                                     CountDownLatch entityReadLatch,
                                     Supplier<BufferData> entitySupplier) {
        return new Http1ServerRequestWithEntity(ctx,
                                                connection,
                                                http1Config,
                                                security,
                                                prologue,
                                                headers,
                                                decoder,
                                                requestId,
                                                expectContinue,
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
    public ListenerContext listenerContext() {
        return ctx.listenerContext();
    }

    @Override
    public HttpSecurity security() {
        return security;
    }

    @Override
    public HttpPrologue prologue() {
        return prologue;
    }

    @Override
    public ServerRequestHeaders headers() {
        return writable == null ? headers : ServerRequestHeaders.create(writable);
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
        return headers.get(HeaderNames.HOST).get();
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
    public Http1ServerRequest path(RoutedPath routedPath) {
        this.path = routedPath;
        return this;
    }

    @Override
    public Http1ServerRequest prologue(HttpPrologue newPrologue) {
        this.prologue = newPrologue;
        return this;
    }

    @Override
    public UriInfo requestedUri() {
        return uriInfo.get();
    }

    @Override
    public Optional<ProxyProtocolData> proxyProtocolData() {
        return ctx.proxyProtocolData();
    }

    @Override
    public String toString() {
        return prologue.rawProtocol() + " " + prologue.method() + " " + path().rawPathNoParams();
    }

    private UriInfo createUriInfo() {
        return ctx.listenerContext().config().requestedUriDiscoveryContext()
                .orElse(DEFAULT_REQUESTED_URI_DISCOVERY_CONTEXT)
                .uriInfo(remotePeer().address().toString(),
                         localPeer().address().toString(),
                         path.absolute().path(),
                         headers,
                         query(),
                         isSecure());
    }
}

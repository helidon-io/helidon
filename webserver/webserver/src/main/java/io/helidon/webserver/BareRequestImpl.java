/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import javax.net.ssl.SSLEngine;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * The BareRequestImpl.
 */
class BareRequestImpl implements BareRequest {

    private final HttpRequest nettyRequest;
    private final Flow.Publisher<DataChunk> publisher;
    private final WebServer webServer;
    private final ChannelHandlerContext ctx;
    private final SSLEngine sslEngine;
    private final long requestId;
    private final URI uri;

    BareRequestImpl(HttpRequest request,
                    Flow.Publisher<DataChunk> publisher,
                    WebServer webServer,
                    ChannelHandlerContext ctx,
                    SSLEngine sslEngine,
                    long requestId) {
        this.nettyRequest = request;
        this.publisher = publisher;
        this.webServer = webServer;
        this.ctx = ctx;
        this.sslEngine = sslEngine;
        this.requestId = requestId;
        this.uri = URI.create(nettyRequest.uri());
    }

    @Override
    public WebServer webServer() {
        return webServer;
    }

    @Override
    public Http.RequestMethod method() {
        return Http.RequestMethod.create(nettyRequest.method().name());
    }

    @Override
    public Http.Version version() {
        return Http.Version.create(nettyRequest.protocolVersion().text());
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public String localAddress() {
        return hostString(ctx.channel().localAddress());
    }

    @Override
    public int localPort() {
        return port(ctx.channel().localAddress());
    }

    @Override
    public String remoteAddress() {
        return hostString(ctx.channel().remoteAddress());
    }

    @Override
    public int remotePort() {
        return port(ctx.channel().remoteAddress());
    }

    private String hostString(SocketAddress address) {
        return address instanceof InetSocketAddress ? ((InetSocketAddress) address).getHostString() : null;
    }

    private int port(SocketAddress address) {
        return address instanceof InetSocketAddress ? ((InetSocketAddress) address).getPort() : -1;
    }

    @Override
    public boolean isSecure() {
        return sslEngine != null;
    }

    @Override
    public Map<String, List<String>> headers() {
        HashMap<String, List<String>> map = new HashMap<>();

        for (Map.Entry<String, String> entry : nettyRequest.headers().entries()) {
            map.computeIfAbsent(entry.getKey(), s -> new ArrayList<>()).add(entry.getValue());
        }

        return map;
    }

    @Override
    public Flow.Publisher<DataChunk> bodyPublisher() {
        return publisher;
    }

    @Override
    public long requestId() {
        return requestId;
    }

    @Override
    public Single<Void> close() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        ctx.close().addListener(f -> {
            if (f.isSuccess()) {
                cf.complete(null);
            } else if (f.isCancelled()) {
                cf.cancel(true);
            } else {
                cf.completeExceptionally(f.cause());
            }
        });
        return Single.create(cf, true);
    }
}

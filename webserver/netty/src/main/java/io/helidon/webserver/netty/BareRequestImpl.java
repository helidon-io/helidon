/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLEngine;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.BareRequest;

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
    }

    @Override
    public WebServer getWebServer() {
        return webServer;
    }

    @Override
    public Http.RequestMethod getMethod() {
        return Http.RequestMethod.from(nettyRequest.method().name());
    }

    @Override
    public Http.Version getVersion() {
        return Http.Version.of(nettyRequest.protocolVersion().text());
    }

    @Override
    public URI getUri() {
        return URI.create(nettyRequest.uri());
    }

    @Override
    public String getLocalAddress() {
        return hostString(ctx.channel().localAddress());
    }

    @Override
    public int getLocalPort() {
        return port(ctx.channel().localAddress());
    }

    @Override
    public String getRemoteAddress() {
        return hostString(ctx.channel().remoteAddress());
    }

    @Override
    public int getRemotePort() {
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
    public Map<String, List<String>> getHeaders() {
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
}

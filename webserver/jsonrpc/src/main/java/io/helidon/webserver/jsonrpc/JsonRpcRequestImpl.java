/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webserver.jsonrpc;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.helidon.common.context.Context;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.media.ReadableEntity;
import io.helidon.jsonrpc.core.JsonRpcParams;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.ServerRequest;

import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * An implementation of a JSON-RPC request.
 */
class JsonRpcRequestImpl implements JsonRpcRequest {

    private final ServerRequest delegate;
    private final JsonObject request;

    JsonRpcRequestImpl(ServerRequest delegate, JsonObject request) {
        this.delegate = delegate;
        this.request = request;
    }

    @Override
    public String version() {
        return request.getString("jsonrpc");
    }

    @Override
    public String rpcMethod() {
        return request.getString("method");
    }

    @Override
    public Optional<JsonValue> rpcId() {
        return Optional.ofNullable(request.get("id"));
    }

    @Override
    public JsonRpcParams params() {
        JsonValue value = request.get("params");
        if (value == null) {
            value = JsonValue.EMPTY_JSON_OBJECT;
        }
        return JsonRpcParams.create((JsonStructure) value);
    }

    @Override
    public JsonObject asJsonObject() {
        return request;
    }

    @Override
    public Context context() {
        return delegate.context();
    }

    @Override
    public ListenerContext listenerContext() {
        return delegate.listenerContext();
    }

    @Override
    public HttpSecurity security() {
        return delegate.security();
    }

    @Override
    public boolean continueSent() {
        return delegate.continueSent();
    }

    @Override
    public void streamFilter(UnaryOperator<InputStream> filterFunction) {
        delegate.streamFilter(filterFunction);
    }

    @Override
    public Optional<ProxyProtocolData> proxyProtocolData() {
        return delegate.proxyProtocolData();
    }

    @Override
    public HttpPrologue prologue() {
        return delegate.prologue();
    }

    @Override
    public ServerRequestHeaders headers() {
        return delegate.headers();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public boolean isSecure() {
        return delegate.isSecure();
    }

    @Override
    public RoutedPath path() {
        return delegate.path();
    }

    @Override
    public ReadableEntity content() {
        return delegate.content();
    }

    @Override
    public String socketId() {
        return delegate.socketId();
    }

    @Override
    public String serverSocketId() {
        return delegate.serverSocketId();
    }

    @Override
    public UriQuery query() {
        return delegate.query();
    }

    @Override
    public PeerInfo remotePeer() {
        return delegate.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return delegate.localPeer();
    }

    @Override
    public String authority() {
        return delegate.authority();
    }

    @Override
    public void header(Header header) {
        delegate.header(header);
    }

    @Override
    public int id() {
        return delegate.id();
    }

    @Override
    public UriInfo requestedUri() {
        return delegate.requestedUri();
    }
}

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

import java.util.Optional;

import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HttpPrologue;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.webserver.http.HttpRequest;

import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;

class JsonRpcRequestImpl implements JsonRpcRequest {

    private final HttpRequest delegate;
    private final JsonObject json;

    JsonRpcRequestImpl(HttpRequest delegate, JsonObject json) {
        this.delegate = delegate;
        this.json = json;
    }

    @Override
    public String version() {
        return json.getString("jsonrpc");
    }

    @Override
    public String method() {
        return json.getString("method");
    }

    @Override
    public Optional<Integer> jsonId() {
        return json.containsKey("id")
                ? Optional.of(json.getInt("id"))
                : Optional.empty();
    }

    @Override
    public JsonRpcParams params() {
        return new JsonRpcParamsImpl((JsonStructure) json.get("params"));
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
    public UriPath path() {
        return delegate.path();
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

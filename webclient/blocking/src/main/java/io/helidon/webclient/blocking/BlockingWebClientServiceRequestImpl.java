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
package io.helidon.webclient.blocking;

import java.net.URI;
import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.http.Parameters;
import io.helidon.webclient.WebClientRequestHeaders;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.WebClientServiceResponse;



class BlockingWebClientServiceRequestImpl implements BlockingWebClientServiceRequest {
    private final WebClientServiceRequest req;

    BlockingWebClientServiceRequestImpl(WebClientServiceRequest req) {
        this.req = req;
    }

    static BlockingWebClientServiceRequest create(WebClientServiceRequest req){
        return new BlockingWebClientServiceRequestImpl(req);
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
    public Path path() {
        return req.path();
    }

    @Override
    public String fragment() {
        return req.fragment();
    }

    @Override
    public WebClientRequestHeaders headers() {
        return req.headers();
    }

    @Override
    public Context context() {
        return req.context();
    }

    @Override
    public long requestId() {
        return req.requestId();
    }

    @Override
    public void requestId(long requestId) {
        req.requestId();
    }

    @Override
    public WebClientServiceRequest whenSent() {
        return req.whenSent().await();
    }

    @Override
    public WebClientServiceResponse whenResponseReceived() {
        return req.whenResponseReceived().await();
    }

    @Override
    public WebClientServiceResponse whenComplete() {
        return req.whenComplete().await();
    }

    @Override
    public Map<String, String> properties() {
        return req.properties();
    }

    @Override
    public String schema() {
        return req.schema();
    }

    @Override
    public void schema(String schema) {
        req.schema(schema);
    }

    @Override
    public String host() {
        return req.host();
    }

    @Override
    public void host(String host) {
        req.host(host);
    }

    @Override
    public int port() {
        return req.port();
    }

    @Override
    public void port(int port) {
        req.port(port);
    }

    @Override
    public void path(String path) {
        req.path(path);
    }

    @Override
    public void fragment(String fragment) {
        req.fragment(fragment);
    }
}

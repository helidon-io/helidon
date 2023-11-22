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

package io.helidon.webclient.http2;

import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.http.Method;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.HttpClientSpi;

class Http2ClientImpl implements Http2Client, HttpClientSpi {
    private final WebClient webClient;
    private final Http2ClientConfig clientConfig;
    private final Http2ClientProtocolConfig protocolConfig;
    private final Http2ConnectionCache connectionCache;
    private final Http2ConnectionCache clientCache;

    Http2ClientImpl(WebClient webClient, Http2ClientConfig clientConfig) {
        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
        if (clientConfig.shareConnectionCache()) {
            this.connectionCache = Http2ConnectionCache.shared();
            this.clientCache = null;
        } else {
            this.connectionCache = Http2ConnectionCache.create();
            this.clientCache = connectionCache;
        }
    }

    @Override
    public Http2ClientRequest method(Method method) {
        ClientUri clientUri = clientConfig.baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty

        UriQueryWriteable query = UriQueryWriteable.create();
        clientConfig.baseQuery().ifPresent(query::from);

        return new Http2ClientRequestImpl(this, method, clientUri, clientConfig.properties());
    }

    @Override
    public Http2ClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        ConnectionKey ck = new ConnectionKey(clientUri.scheme(),
                                             clientUri.host(),
                                             clientUri.port(),
                                             clientRequest.tls(),
                                             clientConfig.dnsResolver(),
                                             clientConfig.dnsAddressLookup(),
                                             clientRequest.proxy());
        if (connectionCache.supports(ck)) {
            return SupportLevel.SUPPORTED;
        }

        return SupportLevel.NOT_SUPPORTED;
    }

    @Override
    public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        Http2ClientRequest request = new Http2ClientRequestImpl(this,
                                                                clientRequest.method(),
                                                                clientUri,
                                                                clientRequest.properties());

        clientRequest.connection().ifPresent(request::connection);
        clientRequest.pathParams().forEach(request::pathParam);

        return request.readTimeout(clientRequest.readTimeout())
                .followRedirects(clientRequest.followRedirects())
                .maxRedirects(clientRequest.maxRedirects())
                .proxy(clientRequest.proxy())
                .tls(clientRequest.tls())
                .headers(clientRequest.headers())
                .fragment(clientUri.fragment());
    }

    @Override
    public void closeResource() {
        if (clientCache != null) {
            this.clientCache.closeResource();
        }
    }

    WebClient webClient() {
        return webClient;
    }

    Http2ClientConfig clientConfig() {
        return clientConfig;
    }

    Http2ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }

    Http2ConnectionCache connectionCache(){
        return connectionCache;
    }
}

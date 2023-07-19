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

package io.helidon.nima.http2.webclient;

import java.util.Optional;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.ClientRequestConfig;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.FullClientRequest;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.spi.HttpClientSpi;

class Http2ClientImpl implements Http2Client, HttpClientSpi {
    private final WebClient client;
    private final Http2ClientConfig clientConfig;
    private final Http2ClientProtocolConfig protocolConfig;

    Http2ClientImpl(WebClient client, Http2ClientConfig clientConfig) {
        this.client = client;
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
    }

    @Override
    public Http2ClientRequest method(Http.Method method) {
        ClientUri clientUri = clientConfig.baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty

        UriQueryWriteable query = UriQueryWriteable.create();
        clientConfig.baseQuery().ifPresent(query::from);

        return new ClientRequestImpl(client, clientConfig, protocolConfig, method, clientUri, clientConfig.tls());
    }

    @Override
    public Http2ClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        return null;
    }

    @Override
    public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        return null;
    }

    @Override
    public Optional<ClientRequest<?>> clientRequest(ClientRequestConfig clientRequestConfig,
                                                    ClientUri clientUri,
                                                    ClientRequestHeaders headers,
                                                    UriQueryWriteable query,
                                                    UriFragment fragment) {

        /*
        this is only available for non-tls requests - for TLS we use ALPN
        0. MUST BE DONE IN WEBCLIENT: check cache if we tried this request before (same connection key)
        1. check if we have prior knowledge configured - if yes, just do it
        2. try to upgrade, falling back to HTTP/1.1
         */
    }
}

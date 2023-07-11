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

package io.helidon.nima.webclient.http1;

import java.util.Optional;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.ClientRequestConfig;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.spi.HttpClientSpi;

class Http1ClientImpl implements Http1Client, HttpClientSpi {
    private final Http1ClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;

    Http1ClientImpl(Http1ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
    }

    @Override
    public Http1ClientRequest method(Http.Method method) {
        ClientUri clientUri = clientConfig.baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty

        UriQueryWriteable query = UriQueryWriteable.create();
        clientConfig.baseQuery().ifPresent(query::from);

        return new ClientRequestImpl(clientConfig, protocolConfig, method, clientUri, query, clientConfig.properties());
    }

    @Override
    public Http1ClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public Optional<ClientRequest<?>> clientRequest(ClientRequestConfig clientRequestConfig,
                                                    ClientUri clientUri,
                                                    ClientRequestHeaders headers,
                                                    UriQueryWriteable query,
                                                    UriFragment fragment) {

        // this is HTTP/1.1 - it should support any and all HTTP requests
        // this method is called from the "generic" HTTP client, that can support any version (that is on classpath).
        // usually HTTP/1.1 is either the only available, or a fallback if other versions cannot be used
        Http1ClientRequest request = new ClientRequestImpl(clientConfig,
                                                           protocolConfig,
                                                           clientRequestConfig.method(),
                                                           clientUri,
                                                           query,
                                                           clientRequestConfig.properties());

        clientRequestConfig.connection().ifPresent(request::connection);
        clientRequestConfig.pathParams().forEach(request::pathParam);
        clientRequestConfig.readTimeout().ifPresent(request::readTimeout);

        request.followRedirects(clientRequestConfig.followRedirects())
                .maxRedirects(clientRequestConfig.maxRedirects())
                .keepAlive(clientRequestConfig.keepAlive())
                .proxy(clientRequestConfig.proxy())
                .tls(clientRequestConfig.tls())
                .headers(headers)
                .fragment(fragment);

        return Optional.of(request);
    }

    HttpClientConfig clientConfig() {
        return clientConfig;
    }

    Http1ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }
}

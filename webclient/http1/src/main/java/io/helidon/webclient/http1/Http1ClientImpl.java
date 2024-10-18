/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.http.Method;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.HttpClientSpi;

class Http1ClientImpl implements Http1Client, HttpClientSpi {
    static final AtomicReference<Http1ClientConfig> GLOBAL_CONFIG = new AtomicReference<>();
    private static final LazyValue<Http1ClientConfig> LAZY_GLOBAL_CONFIG = LazyValue.create(() -> {
        Config config = GlobalConfig.config();
        return Http1ClientConfig.builder()
                .servicesDiscoverServices(false)
                .config(config.get("http1-client"))
                .buildPrototype();
    });

    private final WebClient webClient;
    private final Http1ClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;
    private final Http1ConnectionCache connectionCache;
    private final Http1ConnectionCache clientCache;

    Http1ClientImpl(WebClient webClient, Http1ClientConfig clientConfig) {
        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
        if (clientConfig.shareConnectionCache()) {
            this.connectionCache = Http1ConnectionCache.shared();
            this.clientCache = null;
        } else {
            this.connectionCache = Http1ConnectionCache.create(clientConfig.connectionCacheConfig());
            this.clientCache = connectionCache;
        }
    }

    static Http1ClientConfig globalConfig() {
        return Optional.ofNullable(Http1ClientImpl.GLOBAL_CONFIG.get())
                .orElseGet(Http1ClientImpl.LAZY_GLOBAL_CONFIG);
    }

    @Override
    public Http1ClientRequest method(Method method) {
        ClientUri clientUri = clientConfig.baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty

        clientConfig.baseFragment().ifPresent(clientUri::fragment);
        clientConfig.baseQuery().ifPresent(clientUri.writeableQuery()::from);

        return new Http1ClientRequestImpl(this, method, clientUri, clientConfig.properties());
    }

    @Override
    public Http1ClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        // HTTP/1.1 is compatible with ANY HTTP request
        return SupportLevel.COMPATIBLE;
    }

    @Override
    public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        // this is HTTP/1.1 - it should support any and all HTTP requests
        // this method is called from the "generic" HTTP client, that can support any version (that is on classpath).
        // usually HTTP/1.1 is either the only available, or a fallback if other versions cannot be used
        Http1ClientRequest request = new Http1ClientRequestImpl(this,
                                                                clientRequest.method(),
                                                                clientUri,
                                                                clientRequest.properties());

        clientRequest.connection().ifPresent(request::connection);
        clientRequest.pathParams().forEach(request::pathParam);

        return request.readTimeout(clientRequest.readTimeout())
                .followRedirects(clientRequest.followRedirects())
                .maxRedirects(clientRequest.maxRedirects())
                .keepAlive(clientRequest.keepAlive())
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

    Http1ClientConfig clientConfig() {
        return clientConfig;
    }

    Http1ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }

    Http1ConnectionCache connectionCache() {
        return connectionCache;
    }
}

/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.http.HttpLogConfig;
import io.helidon.http.LogFormatter;
import io.helidon.http.Method;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.http.http1.Http1LoggingConnectionListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.HttpTransportConnectionCache;
import io.helidon.webclient.api.HttpTransportObserverSupport;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.HttpClientSpi;

class Http1ClientImpl implements Http1Client, HttpClientSpi {
    private final WebClient webClient;
    private final Http1ClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;
    private final Http1ConnectionCache connectionCache;
    private final Http1ConnectionCache clientCache;
    private final HttpTransportConnectionCache<Http1ConnectionCache> observedCache;
    private final Http1ConnectionListener recvListener;
    private final Http1ConnectionListener sendListener;
    private final LogFormatter logFormatter;

    Http1ClientImpl(WebClient webClient, Http1ClientConfig clientConfig) {
        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
        this.observedCache = HttpTransportConnectionCache.create(Http1ConnectionCache.class,
                                                                 clientConfig,
                                                                 Http1ConnectionCache::create)
                .orElse(null);
        if (observedCache != null) {
            this.connectionCache = null;
            this.clientCache = null;
        } else if (clientConfig.shareConnectionCache()) {
            this.connectionCache = Http1ConnectionCache.shared();
            this.clientCache = null;
        } else {
            this.connectionCache = Http1ConnectionCache.create();
            this.clientCache = connectionCache;
        }

        HttpLogConfig logConfig = protocolConfig.log();
        if (logConfig.receiveLog()) {
            this.recvListener = Http1LoggingConnectionListener.create(logConfig,
                                                                      "cl-recv");
        } else {
            this.recvListener = Http1ConnectionListener.create(List.of());
        }
        if (logConfig.sendLog()) {
            this.sendListener = Http1LoggingConnectionListener.create(logConfig,
                                                                      "cl-send");
        } else {
            this.sendListener = Http1ConnectionListener.create(List.of());
        }

        this.logFormatter = LogFormatter.create(logConfig);
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
        var selectedProxyRoute = clientRequest.selectedProxyRoute();
        Http1ClientRequestImpl request = new Http1ClientRequestImpl(this,
                                                                    clientRequest,
                                                                    clientRequest.method(),
                                                                    clientUri,
                                                                    clientRequest.sendExpectContinue().orElse(null),
                                                                    clientRequest.properties());

        clientRequest.connection().ifPresent(request::connection);
        clientRequest.pathParams().forEach(request::pathParam);
        clientRequest.address().ifPresent(request::address);
        clientRequest.sni().ifPresent(request::sni);
        request.readTimeout(clientRequest.readTimeout())
                .readContinueTimeout(clientRequest.readContinueTimeout())
                .followRedirects(clientRequest.followRedirects())
                .maxRedirects(clientRequest.maxRedirects())
                .keepAlive(clientRequest.keepAlive())
                .proxy(clientRequest.proxy())
                .tls(clientRequest.tls())
                .headers(clientRequest.headers())
                .fragment(clientUri.fragment());
        selectedProxyRoute.ifPresent(request::selectedProxyRoute);
        return request;
    }

    @Override
    public void closeResource() {
        if (observedCache != null) {
            observedCache.closeResource();
        } else if (clientCache != null) {
            this.clientCache.closeResource();
        }
    }

    @Override
    public CompletionStage<Void> closeResourceAsync() {
        closeResource();
        return observedCache == null ? CompletableFuture.completedStage(null) : observedCache.completion();
    }

    Http1ConnectionListener recvListener() {
        return recvListener;
    }

    Http1ConnectionListener sendListener() {
        return sendListener;
    }

    LogFormatter logFormatter() {
        return logFormatter;
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
        return observedCache == null ? connectionCache : observedCache.cache();
    }

    <T extends ClientConnection> T observe(T connection) {
        return observedCache == null ? connection
                : HttpTransportObserverSupport.observe(connection, observedCache.observer());
    }
}

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

package io.helidon.webclient.http2;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.Method;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.HttpClientSpi;

/**
 * Implementation of HTTP2 client.
 */
public class Http2ClientImpl implements Http2Client, HttpClientSpi {
    private final WebClient webClient;
    private final Http2ClientConfig clientConfig;
    private final Http2ClientProtocolConfig protocolConfig;
    private final Http2ConnectionCache connectionCache;
    private final Http2ConnectionCache clientCache;
    private final AtomicReference<Http1FallbackResources> http1FallbackResources = new AtomicReference<>();
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final Http2FrameListener sendListener;
    private final Http2FrameListener recvListener;
    private volatile boolean closed;

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

        HttpLogConfig log = protocolConfig.log();
        if (log.receiveLog()) {
            recvListener = Http2LoggingFrameListener.create(log, "cl-recv");
        } else {
            recvListener = Http2FrameListener.create(List.of());
        }
        if (log.sendLog()) {
            sendListener = Http2LoggingFrameListener.create(log, "cl-send");
        } else {
            sendListener = Http2FrameListener.create(List.of());
        }
    }

    @Override
    public Http2ClientRequest method(Method method) {
        ClientUri clientUri = clientConfig.baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty

        UriQueryWriteable query = UriQueryWriteable.create();
        clientConfig.baseQuery().ifPresent(query::from);

        return new Http2ClientRequestImpl(this, null, method, clientUri, clientConfig.properties());
    }

    @Override
    public Http2ClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        if (connectionCache.supports(Http2ConnectionKeys.create(clientUri, clientRequest, clientConfig))) {
            return SupportLevel.SUPPORTED;
        }

        return SupportLevel.NOT_SUPPORTED;
    }

    @Override
    public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        Http2ClientRequestImpl request = new Http2ClientRequestImpl(this,
                                                                    clientRequest,
                                                                    clientRequest.method(),
                                                                    clientUri,
                                                                    clientRequest.properties(),
                                                                    genericTcpProtocolIds());

        clientRequest.connection().ifPresent(request::connection);
        clientRequest.pathParams().forEach(request::pathParam);
        clientRequest.address().ifPresent(request::address);
        clientRequest.sni().ifPresent(request::sni);

        return request.readTimeout(clientRequest.readTimeout())
                .readContinueTimeout(clientRequest.readContinueTimeout())
                .followRedirects(clientRequest.followRedirects())
                .maxRedirects(clientRequest.maxRedirects())
                .proxy(clientRequest.proxy())
                .tls(clientRequest.tls())
                .headers(clientRequest.headers())
                .fragment(clientUri.fragment());
    }

    @Override
    public void closeResource() {
        Http1FallbackResources fallbackResources;
        lifecycleLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            fallbackResources = http1FallbackResources.getAndSet(null);
        } finally {
            lifecycleLock.unlock();
        }

        try {
            if (fallbackResources != null) {
                fallbackResources.closeResource();
            }
        } finally {
            if (clientCache != null) {
                this.clientCache.closeResource();
            }
        }
    }

    WebClient webClient() {
        return webClient;
    }

    Http1Client http1FallbackClient() {
        if (closed) {
            throw new IllegalStateException("HTTP/2 client is closed");
        }

        Http1FallbackResources fallbackResources = http1FallbackResources.get();
        if (fallbackResources != null) {
            return fallbackResources.http1Client();
        }

        lifecycleLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("HTTP/2 client is closed");
            }
            fallbackResources = http1FallbackResources.get();
            if (fallbackResources != null) {
                return fallbackResources.http1Client();
            }

            WebClient fallbackWebClient = WebClientConfig.builder(webClient.prototype())
                    .clearServices()
                    .servicesDiscoverServices(false)
                    .addService(new Http1FallbackService())
                    .cookieManager(WebClientCookieManager.builder().build())
                    .protocolPreference(List.of(Http1Client.PROTOCOL_ID))
                    .shareConnectionCache(false)
                    .build();
            try {
                Http1Client http1Client = fallbackWebClient.client(Http1Client.PROTOCOL);
                fallbackResources = new Http1FallbackResources(fallbackWebClient, http1Client);
                http1FallbackResources.set(fallbackResources);
                return http1Client;
            } catch (RuntimeException | Error e) {
                fallbackWebClient.closeResource();
                throw e;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    List<String> genericTcpProtocolIds() {
        List<String> protocolPreference = webClient.prototype().protocolPreference();
        if (protocolPreference.isEmpty()) {
            return List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID);
        }
        return protocolPreference;
    }

    Http2ClientConfig clientConfig() {
        return clientConfig;
    }

    Http2ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }

    Http2ConnectionCache connectionCache() {
        return connectionCache;
    }

    Http2FrameListener sendListener() {
        return sendListener;
    }

    Http2FrameListener recvListener() {
        return recvListener;
    }

    private static final class Http1FallbackResources {
        private final WebClient webClient;
        private final Http1Client http1Client;

        private Http1FallbackResources(WebClient webClient, Http1Client http1Client) {
            this.webClient = webClient;
            this.http1Client = http1Client;
        }

        private Http1Client http1Client() {
            return http1Client;
        }

        void closeResource() {
            try {
                http1Client.closeResource();
            } finally {
                webClient.closeResource();
            }
        }
    }
}

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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.ProxyRoute;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientProtocolResponse;
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
    private final boolean altSvcNotificationsEnabled;
    private final boolean altSvcEnabled;
    private final boolean responseNotificationsManagedByWebClient;
    private volatile boolean closed;

    Http2ClientImpl(WebClient webClient, Http2ClientConfig clientConfig) {
        this(webClient, clientConfig, false);
    }

    Http2ClientImpl(WebClient webClient,
                    Http2ClientConfig clientConfig,
                    boolean responseNotificationsManagedByWebClient) {
        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
        Optional<ClientAltSvcConfig> altSvc = clientConfig.altSvc()
                .filter(ClientAltSvcConfig::enabled);
        this.altSvcNotificationsEnabled = altSvc.isPresent();
        this.altSvcEnabled = altSvc
                .map(config -> config.protocols().isEmpty()
                        || config.protocols().contains(Http2Client.PROTOCOL_ID))
                .orElse(false);
        this.responseNotificationsManagedByWebClient = responseNotificationsManagedByWebClient;
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
        ConnectionKey connectionKey = Http2ConnectionKeys.create(clientUri, clientRequest, clientConfig);
        if (connectionCache.supports(connectionKey)) {
            return SupportLevel.SUPPORTED;
        }

        if (altSvcEnabled) {
            boolean explicitConnection = clientRequest.connection().isPresent();
            if (!connectionCache.mayContainAlternative(connectionKey.host(), explicitConnection)) {
                return SupportLevel.NOT_SUPPORTED;
            }
            ClientRequestHeaders headers = clientRequest.headers();
            if (clientRequest.sni()
                    .or(clientConfig::sni)
                    .filter(sni -> sni.mode() == SniMode.HOST_HEADER)
                    .isPresent()) {
                connectionKey = Http2ConnectionKeys.create(clientUri, clientRequest, clientConfig, headers);
            }
            Optional<ProxyRoute> selectedRoute = clientRequest.selectedProxyRoute();
            boolean alternativeAvailable;
            if (selectedRoute.isPresent()) {
                ClientConnectionTarget target = ClientConnectionTarget.create(connectionKey,
                                                                                clientUri,
                                                                                headers,
                                                                                selectedRoute.get());
                alternativeAvailable = connectionCache.alternativeAvailable(target, explicitConnection);
            } else {
                ClientConnectionTarget.LookupKey lookupKey = ClientConnectionTarget.lookupKey(connectionKey,
                                                                                               clientUri,
                                                                                               headers);
                Http2AltSvcCache.Candidate candidate = connectionCache.currentAlternative(lookupKey,
                                                                                           explicitConnection);
                if (candidate == null) {
                    alternativeAvailable = false;
                } else {
                    boolean originAuthorityOverride = headers.contains(Http2Headers.AUTHORITY_NAME)
                            || headers.contains(HeaderNames.HOST);
                    ClientConnectionTarget target = originAuthorityOverride
                            ? ClientConnectionTarget.create(connectionKey,
                                                            clientUri,
                                                            headers,
                                                            candidate.proxyRoute())
                            : ClientConnectionTarget.create(connectionKey,
                                                            clientUri.scheme(),
                                                            candidate.proxyRoute());
                    alternativeAvailable = connectionCache.alternativeAvailable(target, explicitConnection);
                }
            }
            if (alternativeAvailable) {
                return SupportLevel.SUPPORTED;
            }
        }

        return SupportLevel.NOT_SUPPORTED;
    }

    @Override
    public void responseReceived(WebClientProtocolResponse response) {
        if (!altSvcEnabled
                || !response.secure()
                || !(Http1Client.PROTOCOL_ID.equals(response.protocolId())
                        || Http2Client.PROTOCOL_ID.equals(response.protocolId()))
                || response.status().code() == Status.MISDIRECTED_REQUEST_421_CODE) {
            return;
        }

        var receivedAt = response.receivedAt();
        AltSvcHeader.create(response.headers(), receivedAt)
                .ifPresent(header -> connectionCache.recordAlternative(response.target().logicalTarget(),
                                                                        header,
                                                                        response.secure(),
                                                                        response.explicitConnection(),
                                                                        receivedAt));
    }

    @Override
    public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        var selectedProxyRoute = clientRequest.selectedProxyRoute();
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
        request.readTimeout(clientRequest.readTimeout())
                .readContinueTimeout(clientRequest.readContinueTimeout())
                .followRedirects(clientRequest.followRedirects())
                .maxRedirects(clientRequest.maxRedirects())
                .proxy(clientRequest.proxy())
                .tls(clientRequest.tls())
                .headers(clientRequest.headers())
                .fragment(clientUri.fragment());
        selectedProxyRoute.ifPresent(request::selectedProxyRoute);
        return request;
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
                var provider = Http1Client.PROTOCOL.provider();
                var configType = provider.configType();
                var http1ProtocolConfig = fallbackWebClient.prototype()
                        .protocolConfigs()
                        .stream()
                        .filter(config -> provider.protocolId().equals(config.type()))
                        .filter(config -> configType.isAssignableFrom(config.getClass()))
                        .map(configType::cast)
                        .findFirst()
                        .orElseGet(provider::defaultConfig);
                WebClient forwardingWebClient = new Http2ResponseForwardingWebClient(fallbackWebClient,
                                                                                      this::publishResponse);
                Http1Client http1Client = provider.protocol(forwardingWebClient, http1ProtocolConfig);
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

    boolean altSvcEnabled() {
        return altSvcEnabled;
    }

    boolean altSvcNotificationsEnabled() {
        return altSvcNotificationsEnabled;
    }

    boolean responseNotificationsManagedByWebClient() {
        return responseNotificationsManagedByWebClient;
    }

    void publishResponse(WebClientProtocolResponse response) {
        if (responseNotificationsManagedByWebClient) {
            webClient.responseReceived(response);
        } else {
            responseReceived(response);
        }
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

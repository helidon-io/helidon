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

package io.helidon.webserver.testing.junit5;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.Method;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.HttpRouting;

/**
 * Unit testing client that bypasses HTTP transport and directly invokes router.
 */
public class DirectWebClient implements WebClient {
    private final HttpRouting routing;
    private final WebClient webClient;

    private String clientHost;
    private int clientPort;
    private Principal clientTlsPrincipal;
    private Certificate[] clientTlsCertificates;
    private String serverHost;
    private int serverPort;
    private Principal serverTlsPrincipal;
    private Certificate[] serverTlsCertificates;
    private boolean isTls;
    private Router router;

    /**
     * Create a direct client for HTTP routing.
     *
     * @param routing routing to use
     */
    public DirectWebClient(HttpRouting.Builder routing) {
        this.routing = routing.build();
        this.webClient = WebClient.builder()
                .baseUri("http://helidon-unit:65000")
                .build();
        this.router = Router.builder().addRouting(routing).build();
    }

    @Override
    public HttpClientRequest method(Method method) {
        if (clientHost == null) {
            clientHost = "localhost";
        }
        if (clientPort == 0) {
            clientPort = 64000;
        }
        if (serverPort == 0) {
            serverPort = 8080;
        }
        if (serverHost == null) {
            serverHost = "localhost";
        }
        PeerInfo clientPeer = new DirectPeerInfo(
                InetSocketAddress.createUnresolved(clientHost, clientPort),
                clientHost,
                clientPort,
                Optional.ofNullable(clientTlsPrincipal),
                Optional.ofNullable(clientTlsCertificates));

        PeerInfo localPeer = new DirectPeerInfo(
                InetSocketAddress.createUnresolved(serverHost, serverPort),
                serverHost,
                serverPort,
                Optional.ofNullable(serverTlsPrincipal),
                Optional.ofNullable(serverTlsCertificates));

        HelidonSocket socket = DirectSocket.create(clientPeer, localPeer, isTls);
        return webClient.method(method)
                .connection(new DirectClientConnection(socket, router));
    }

    @Override
    public void closeResource() {
        // Nothing to close in connection-less client
    }

    @Override
    public WebClientConfig prototype() {
        return webClient.prototype();
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
        throw new UnsupportedOperationException("Clients based on protocols cannot be used with DirectWebClient, please inject"
                                                        + " protocol specific direct clients directly.");
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
        throw new UnsupportedOperationException("Clients based on protocols cannot be used with DirectWebClient, please inject"
                                                        + " protocol specific direct clients directly.");
    }

    @Override
    public ExecutorService executor() {
        return webClient.executor();
    }

    @Override
    public WebClientCookieManager cookieManager() {
        return webClient.cookieManager();
    }

    /**
     * Whether to use tls (mark this connection as secure).
     *
     * @param tls use tls
     * @return updated client
     */
    public DirectWebClient setTls(boolean tls) {
        isTls = tls;
        return this;
    }

    /**
     * Client host.
     *
     * @param clientHost client host to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient clientHost(String clientHost) {
        this.clientHost = clientHost;
        return this;
    }

    /**
     * Client port.
     *
     * @param clientPort client port to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient clientPort(int clientPort) {
        this.clientPort = clientPort;
        return this;
    }

    /**
     * Client peer TLS principal.
     *
     * @param clientTlsPrincipal principal to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient clientTlsPrincipal(Principal clientTlsPrincipal) {
        this.clientTlsPrincipal = clientTlsPrincipal;
        return this;
    }

    /**
     * Client peer TLS certificates.
     *
     * @param clientTlsCertificates certificates to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient clientTlsCertificates(Certificate[] clientTlsCertificates) {
        this.clientTlsCertificates = clientTlsCertificates;
        return this;
    }

    /**
     * Server host.
     *
     * @param serverHost server host to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient serverHost(String serverHost) {
        this.serverHost = serverHost;
        return this;
    }

    /**
     * Server port.
     *
     * @param serverPort server port to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient serverPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    /**
     * Server TLS principal.
     *
     * @param serverTlsPrincipal principal to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient serverTlsPrincipal(Principal serverTlsPrincipal) {
        this.serverTlsPrincipal = serverTlsPrincipal;
        return this;
    }

    /**
     * Server TLS certificates.
     *
     * @param serverTlsCertificates certificates to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectWebClient serverTlsCertificates(Certificate[] serverTlsCertificates) {
        this.serverTlsCertificates = serverTlsCertificates;
        return this;
    }

    /**
     * Call this method once testing is done, to carry out after stop operations on routers.
     */
    public void close() {
        this.router.afterStop();
    }
}

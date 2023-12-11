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
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Optional;

import io.helidon.common.socket.PeerInfo;
import io.helidon.http.Method;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.HttpRouting;

/**
 * Unit testing client that bypasses HTTP transport and directly invokes router.
 */
public class DirectClient implements Http1Client {
    private final HttpRouting routing;
    private final Http1Client httpClient;

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
    public DirectClient(HttpRouting.Builder routing) {
        this.routing = routing.build();
        this.httpClient = Http1Client.builder()
                .baseUri(URI.create("http://helidon-unit:65000"))
                .build();
        this.router = Router.builder().addRouting(routing).build();
    }

    @Override
    public Http1ClientConfig prototype() {
        return Http1ClientConfig.create();
    }

    @Override
    public Http1ClientRequest method(Method method) {
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

        DirectSocket socket = DirectSocket.create(localPeer, clientPeer, isTls);

        return httpClient.method(method)
                .connection(new DirectClientConnection(socket, router));
    }

    @Override
    public void closeResource() {
        // Nothing to close in connection-less client
    }

    /**
     * Whether to use tls (mark this connection as secure).
     *
     * @param tls use tls
     * @return updated client
     */
    public DirectClient setTls(boolean tls) {
        isTls = tls;
        return this;
    }

    /**
     * Client host.
     *
     * @param clientHost client host to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient clientHost(String clientHost) {
        this.clientHost = clientHost;
        return this;
    }

    /**
     * Client port.
     *
     * @param clientPort client port to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient clientPort(int clientPort) {
        this.clientPort = clientPort;
        return this;
    }

    /**
     * Client peer TLS principal.
     *
     * @param clientTlsPrincipal principal to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient clientTlsPrincipal(Principal clientTlsPrincipal) {
        this.clientTlsPrincipal = clientTlsPrincipal;
        return this;
    }

    /**
     * Client peer TLS certificates.
     *
     * @param clientTlsCertificates certificates to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient clientTlsCertificates(Certificate[] clientTlsCertificates) {
        this.clientTlsCertificates = clientTlsCertificates;
        return this;
    }

    /**
     * Server host.
     *
     * @param serverHost server host to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient serverHost(String serverHost) {
        this.serverHost = serverHost;
        return this;
    }

    /**
     * Server port.
     *
     * @param serverPort server port to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient serverPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    /**
     * Server TLS principal.
     *
     * @param serverTlsPrincipal principal to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient serverTlsPrincipal(Principal serverTlsPrincipal) {
        this.serverTlsPrincipal = serverTlsPrincipal;
        return this;
    }

    /**
     * Server TLS certificates.
     *
     * @param serverTlsCertificates certificates to use in {@link io.helidon.common.socket.PeerInfo}
     * @return updated client
     */
    public DirectClient serverTlsCertificates(Certificate[] serverTlsCertificates) {
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

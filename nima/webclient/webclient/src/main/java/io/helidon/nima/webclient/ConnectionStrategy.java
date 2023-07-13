/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Optional;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.Method;
import io.helidon.common.http.Http.Status;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.socket.TlsSocket;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.Proxy.ProxyType;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webclient.spi.DnsResolver;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Deals with the complexity of creating a connection to a server, with different HTTP protocols, TLS and
 * proxy.
 *
 */
public final class ConnectionStrategy {

    private static final System.Logger LOGGER = System.getLogger(ConnectionStrategy.class.getName());
    private final String host;
    private final int port;
    private final Proxy proxy;
    private final Tls tls;
    private final SocketOptions socketOptions;
    private final DnsResolver dnsResolver;
    private final DnsAddressLookup dnsAddressLookup;
    private final boolean keepAlive;
    private String channelId;
    private Socket socket;
    private HelidonSocket helidonSocket;

    private ConnectionStrategy(String host, int port, Proxy proxy, Tls tls, SocketOptions socketOptions,
            DnsResolver dnsResolver, DnsAddressLookup dnsAddressLookup, boolean keepAlive) {
        this.host = host;
        this.port = port;
        this.proxy = proxy;
        this.tls = tls;
        this.socketOptions = socketOptions;
        this.dnsResolver = dnsResolver;
        this.dnsAddressLookup = dnsAddressLookup;
        this.keepAlive = keepAlive;
    }

    private void connect() {
        ConnectionType strategy;
        try {
            socket = new Socket();
            channelId = createChannelId(socket);
            socket.setSoTimeout((int) socketOptions.readTimeout().toMillis());
            socketOptions.configureSocket(socket);
            InetSocketAddress remoteAddress = inetSocketAddress();
            strategy = ConnectionType.get(proxy, tls, remoteAddress);
            strategy.connect(this, remoteAddress);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not connect to " + host + ":" + port, e);
        }

        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, String.format("[%s] client connected %s %s",
                                            channelId,
                                            socket.getLocalAddress(),
                                            Thread.currentThread().getName()));
        }
    }

    private InetSocketAddress inetSocketAddress() {
        if (dnsResolver.useDefaultJavaResolver()) {
            return new InetSocketAddress(host, port);
        } else {
            InetAddress address = dnsResolver.resolveAddress(host, dnsAddressLookup);
            return new InetSocketAddress(address, port);
        }
    }

    private int connectToProxy(InetSocketAddress remoteAddress, InetSocketAddress proxyAddress) {
        String hostPort = remoteAddress.getHostName() + ":" + remoteAddress.getPort();
        UriHelper uriHelper = UriHelper.create();
        uriHelper.scheme("http");
        uriHelper.host(proxyAddress.getHostName());
        uriHelper.port(proxyAddress.getPort());
        /*
         * Example:
         * CONNECT www.youtube.com:443 HTTP/1.1
         * User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:108.0) Gecko/20100101 Firefox/108.0
         * Proxy-Connection: keep-alive
         * Connection: keep-alive
         * Host: www.youtube.com:443
         */
        Http1Client client = Http1Client.builder().baseUri(uriHelper.toUri())
                .channelOptions(socketOptions).dnsResolver(dnsResolver)
                .dnsAddressLookup(dnsAddressLookup).build();
        Http1ClientRequest request = client.method(Method.CONNECT)
                // This avoids caching the connection
                .proxy(Proxy.TUNNELING)
                .header(Header.HOST, hostPort);
        if (keepAlive) {
            request = request.header(Header.CONNECTION, "keep-alive")
                    .header(Header.PROXY_CONNECTION, "keep-alive");
        }
        Http1ClientResponse response  = request.request();

        // Re-use socket
        this.socket = response.connection().socket();

        return response.status().code();
    }

    private void debugTls(SSLSocket sslSocket) {
        SSLSession sslSession = sslSocket.getSession();
        if (sslSession == null) {
            LOGGER.log(TRACE, "No SSL session");
            return;
        }

        String msg = "[client " + channelId + "] TLS negotiated:\n"
                + "Application protocol: " + sslSocket.getApplicationProtocol() + "\n"
                + "Handshake application protocol: " + sslSocket.getHandshakeApplicationProtocol() + "\n"
                + "Protocol: " + sslSession.getProtocol() + "\n"
                + "Cipher Suite: " + sslSession.getCipherSuite() + "\n"
                + "Peer host: " + sslSession.getPeerHost() + "\n"
                + "Peer port: " + sslSession.getPeerPort() + "\n"
                + "Application buffer size: " + sslSession.getApplicationBufferSize() + "\n"
                + "Packet buffer size: " + sslSession.getPacketBufferSize() + "\n"
                + "Local principal: " + sslSession.getLocalPrincipal() + "\n";

        try {
            msg += "Peer principal: " + sslSession.getPeerPrincipal() + "\n";
            msg += "Peer certs: " + certsToString(sslSession.getPeerCertificates()) + "\n";
        } catch (SSLPeerUnverifiedException e) {
            msg += "Peer not verified";
        }

        LOGGER.log(TRACE, msg);
    }

    private String createChannelId(Socket socket) {
        return "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
    }

    private String certsToString(Certificate[] peerCertificates) {
        String[] certs = new String[peerCertificates.length];

        for (int i = 0; i < peerCertificates.length; i++) {
            Certificate peerCertificate = peerCertificates[i];
            if (peerCertificate instanceof X509Certificate x509) {
                certs[i] = "type=" + peerCertificate.getType()
                        + ";key=" + peerCertificate.getPublicKey().getAlgorithm()
                        + "(" + peerCertificate.getPublicKey().getFormat() + ")"
                        + ";x509=V" + x509.getVersion()
                        + ";from=" + x509.getNotBefore()
                        + ";to=" + x509.getNotAfter()
                        + ";serial=" + x509.getSerialNumber().toString(16);
            } else {
                certs[i] = "type=" + peerCertificate.getType() + ";key=" + peerCertificate.getPublicKey();
            }

        }

        return String.join(", ", certs);
    }

    /**
     * Obtains a ConnectionValues having the data initialized.
     *
     * @param host uri address host
     * @param port uri address port
     * @param proxy Proxy server to use for outgoing requests
     * @param tls TLS to be used in connection
     * @param socketOptions the socket options
     * @param dnsResolver DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param keepAlive specifies whether the connection will be opened
     * @return the ConnectionValues having the data initialized
     */
    public static ConnectionValues connect(String host, int port, Proxy proxy, Tls tls, SocketOptions socketOptions,
            DnsResolver dnsResolver, DnsAddressLookup dnsAddressLookup, boolean keepAlive) {
        // FIXME Some args are Optional
        ConnectionStrategy srategy = new ConnectionStrategy(host, port, proxy, tls, socketOptions,
                dnsResolver, dnsAddressLookup, keepAlive);
        srategy.connect();
        return new ConnectionValues(srategy.channelId, srategy.socket, srategy.helidonSocket);
    }

    /**
     * Connection values containing the scoket and related information.
     *
     * @param channelId unique identifier of socket
     * @param socket the connected socket
     * @param helidonSocket the connected helidonSocket
     */
    public record ConnectionValues(String channelId, Socket socket, HelidonSocket helidonSocket) { }

    private enum ConnectionType {
        PLAIN {
            @Override
            protected void connect(ConnectionStrategy connection, InetSocketAddress remoteAddress) throws IOException {
                connection.socket.connect(remoteAddress, (int) connection.socketOptions.connectTimeout().toMillis());
                connection.helidonSocket = PlainSocket.client(connection.socket, connection.channelId);
            }
        },
        HTTPS {
            @Override
            protected void connect(ConnectionStrategy connection, InetSocketAddress remoteAddress) throws IOException {
                // FIXME should it change for http/2.0?
                SSLSocket sslSocket = connection.tls.createSocket("http/1.1");
                connection.socket = sslSocket;
                connection.socket.connect(remoteAddress, (int) connection.socketOptions.connectTimeout().toMillis());
                sslSocket.startHandshake();
                connection.helidonSocket = TlsSocket.client(sslSocket, connection.channelId);
                if (LOGGER.isLoggable(TRACE)) {
                    connection.debugTls(sslSocket);
                }
            }
        },
        PROXY_PLAIN {
            @Override
            protected void connect(ConnectionStrategy connection, InetSocketAddress remoteAddress) throws IOException {
                UriHelper uri = UriHelper.create();
                uri.scheme("http");
                uri.host(remoteAddress.getHostName());
                uri.port(remoteAddress.getPort());
                InetSocketAddress proxyAddress = connection.proxy.address(uri).get();
                int responseCode = connection.connectToProxy(remoteAddress, proxyAddress);
                if (responseCode != Status.OK_200.code()) {
                    throw new IllegalStateException("Proxy sent wrong HTTP response code: " + responseCode);
                }
                connection.helidonSocket = PlainSocket.client(connection.socket, connection.channelId);
            }
        },
        PROXY_HTTPS {
            @Override
            protected void connect(ConnectionStrategy connection, InetSocketAddress remoteAddress) throws IOException {
                UriHelper uri = UriHelper.create();
                uri.scheme("http");
                uri.host(remoteAddress.getHostName());
                uri.port(remoteAddress.getPort());
                InetSocketAddress proxyAddress = connection.proxy.address(uri).get();
                int responseCode = connection.connectToProxy(remoteAddress, proxyAddress);
                if (responseCode != Status.OK_200.code()) {
                    throw new IllegalStateException("Proxy sent wrong HTTP response code: " + responseCode);
                }
                SSLSocket sslSocket = connection.tls.createSocket("http/1.1", connection.socket, remoteAddress);
                connection.socket = sslSocket;
                sslSocket.startHandshake();
                connection.helidonSocket = TlsSocket.client(sslSocket, connection.channelId);
                if (LOGGER.isLoggable(TRACE)) {
                    connection.debugTls(sslSocket);
                }
            }
        };

        protected abstract void connect(ConnectionStrategy connection, InetSocketAddress remoteAddress) throws IOException;

        static ConnectionType get(Proxy proxy, Tls tls, InetSocketAddress remoteAddress) {
            boolean useProxy = false;
            if (proxy != null && proxy.type() != ProxyType.NONE) {
                UriHelper uri = UriHelper.create();
                uri.scheme("http");
                uri.host(remoteAddress.getHostName());
                uri.port(remoteAddress.getPort());
                if (proxy.type() == ProxyType.SYSTEM) {
                    Optional<InetSocketAddress> optional = proxy.address(uri);
                    useProxy = optional.isPresent();
                } else if (!proxy.isNoHosts(uri)) {
                    useProxy = true;
                }
            }
            if (useProxy) {
                return tls != null ? ConnectionType.PROXY_HTTPS : ConnectionType.PROXY_PLAIN;
            } else {
                return tls != null ? ConnectionType.HTTPS : ConnectionType.PLAIN;
            }
        }
    }

}

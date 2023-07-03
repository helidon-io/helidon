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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.Method;
import io.helidon.common.http.Http.Status;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.socket.TlsSocket;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.Proxy;
import io.helidon.nima.webclient.Proxy.ProxyType;
import io.helidon.nima.webclient.UriHelper;
import io.helidon.nima.webclient.spi.DnsResolver;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

class Http1ClientConnection implements ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientConnection.class.getName());
    private static final long QUEUE_TIMEOUT = 10;
    private static final TimeUnit QUEUE_TIMEOUT_TIME_UNIT = TimeUnit.MILLISECONDS;

    private final LinkedBlockingDeque<Http1ClientConnection> connectionQueue;
    private final ConnectionKey connectionKey;
    private final io.helidon.common.socket.SocketOptions options;
    private final boolean keepAlive;
    private String channelId;
    private Socket socket;
    private HelidonSocket helidonSocket;
    private DataReader reader;
    private DataWriter writer;

    Http1ClientConnection(SocketOptions options, ConnectionKey connectionKey) {
        this(options, null, connectionKey);
    }

    Http1ClientConnection(SocketOptions options,
                          LinkedBlockingDeque<Http1ClientConnection> connectionQueue,
                          ConnectionKey connectionKey) {
        this.options = options;
        this.connectionQueue = connectionQueue;
        this.keepAlive = (connectionQueue != null);
        this.connectionKey = connectionKey;
    }

    @Override
    public DataReader reader() {
        return reader;
    }

    @Override
    public DataWriter writer() {
        return writer;
    }

    @Override
    public void release() {
        finishRequest();
    }

    @Override
    public void close() {
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public void readTimeout(Duration readTimeout) {
        if (!isConnected()) {
            throw new IllegalStateException("Read timeout cannot be set, because connection has not been established.");
        }
        try {
            socket.setSoTimeout((int) readTimeout.toMillis());
        } catch (SocketException e) {
            throw new UncheckedIOException("Could not set read timeout to the connection with the channel id: " + channelId, e);
        }
    }

    boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    private InetSocketAddress inetSocketAddress() {
        DnsResolver dnsResolver = connectionKey.dnsResolver();
        if (dnsResolver.useDefaultJavaResolver()) {
            return new InetSocketAddress(connectionKey.host(), connectionKey.port());
        } else {
            InetAddress address = dnsResolver.resolveAddress(connectionKey.host(), connectionKey.dnsAddressLookup());
            return new InetSocketAddress(address, connectionKey.port());
        }
    }

    private Http1ClientConnection proxyConnection(InetSocketAddress proxyAddress) {
        return new Http1ClientConnection(options, new ConnectionKey("http",
                proxyAddress.getHostName(),
                proxyAddress.getPort(),
                null,
                connectionKey.dnsResolver(),
                connectionKey.dnsAddressLookup(),
                null)).connect();
    }

    private int connectToProxy(InetSocketAddress remoteAddress, InetSocketAddress proxyAddress) {
        String hostPort = remoteAddress.getHostName() + ":" + remoteAddress.getPort();
        Http1ClientConnection proxyConnection = proxyConnection(proxyAddress);
        UriHelper uriHelper = UriHelper.create();
        uriHelper.scheme("http");
        uriHelper.host(proxyAddress.getHostName());
        uriHelper.port(proxyAddress.getPort());
        Http1ClientConfig clientConfig = Http1ClientConfig.builder().mediaContext(MediaContext.create())
                .socketOptions(options).dnsResolver(connectionKey.dnsResolver())
                .dnsAddressLookup(connectionKey.dnsAddressLookup()).defaultHeaders(WritableHeaders.create()).build();
        ClientRequestImpl httpClient = new ClientRequestImpl(clientConfig,
                Method.CONNECT, uriHelper, UriQueryWriteable.create(), Collections.emptyMap());
        httpClient.connection(proxyConnection);
        httpClient.header(Header.HOST, hostPort);
        httpClient.header(Header.ACCEPT, "*/*");
        Http1ClientResponse response  = httpClient.request();

        // Re-use socket
        this.socket = proxyConnection.socket;
        // Note that Http1StatusParser fails parsing HTTP/1.0 and some proxies will return that.
        return response.status().code();
    }

    private String createChannelId(Socket socket) {
        return "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
    }

    Http1ClientConnection connect() {
        ConnectionStrategy strategy;
        try {
            socket = new Socket();
            socket.setSoTimeout((int) options.readTimeout().toMillis());
            options.configureSocket(socket);
            InetSocketAddress remoteAddress = inetSocketAddress();
            strategy = ConnectionStrategy.get(this, remoteAddress);
            strategy.connect(this, remoteAddress);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not connect to " + connectionKey.host() + ":" + connectionKey.port(), e);
        }

        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, String.format("[%s] client connected %s %s",
                                            channelId,
                                            socket.getLocalAddress(),
                                            Thread.currentThread().getName()));
        }
        this.channelId = createChannelId(socket);
        this.reader = new DataReader(helidonSocket);
        this.writer = new DataWriter() {
            @Override
            public void write(BufferData... buffers) {
                writeNow(buffers);
            }

            @Override
            public void write(BufferData buffer) {
                writeNow(buffer);
            }

            @Override
            public void writeNow(BufferData... buffers) {
                for (BufferData buffer : buffers) {
                    writeNow(buffer);
                }
            }

            @Override
            public void writeNow(BufferData buffer) {
                helidonSocket.write(buffer);
            }
        };

        return this;
    }

    void finishRequest() {
        if (keepAlive && connectionQueue != null && socket.isConnected()) {
            try {
                if (connectionQueue.offer(this, QUEUE_TIMEOUT, QUEUE_TIMEOUT_TIME_UNIT)) {
                    LOGGER.log(DEBUG, () -> String.format("[%s] client connection returned %s",
                                                          channelId,
                                                          Thread.currentThread().getName()));
                    return;
                } else {
                    LOGGER.log(DEBUG, () -> String.format("[%s] Unable to return client connection because queue is full %s",
                                                          channelId,
                                                          Thread.currentThread().getName()));
                }
            } catch (InterruptedException ie) {
                LOGGER.log(DEBUG, () -> String.format("[%s] Unable to return client connection due to '%s' %s",
                                                    channelId,
                                                    ie.getMessage(),
                                                    Thread.currentThread().getName()));
            }
        }
        // Close if unable to add to queue
        close();
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

    private enum ConnectionStrategy {
        PLAIN {
            @Override
            protected void connect(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException {
                connection.socket.connect(remoteAddress, (int) connection.options.connectTimeout().toMillis());
                connection.helidonSocket = PlainSocket.client(connection.socket, connection.channelId);
            }
        },
        HTTPS {
            @Override
            protected void connect(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException {
                SSLSocket sslSocket = connection.connectionKey.tls().createSocket("http/1.1");
                connection.socket = sslSocket;
                connection.socket.connect(remoteAddress, (int) connection.options.connectTimeout().toMillis());
                sslSocket.startHandshake();
                connection.helidonSocket = TlsSocket.client(sslSocket, connection.channelId);
                if (LOGGER.isLoggable(TRACE)) {
                    connection.debugTls(sslSocket);
                }
            }
        },
        PROXY_PLAIN {
            @Override
            protected void connect(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException {
                UriHelper uri = UriHelper.create();
                uri.scheme("http");
                uri.host(remoteAddress.getHostName());
                uri.port(remoteAddress.getPort());
                InetSocketAddress proxyAddress = connection.connectionKey.proxy().address(uri).get();
                int responseCode = connection.connectToProxy(remoteAddress, proxyAddress);
                if (responseCode != Status.OK_200.code()) {
                    throw new IllegalStateException("Proxy sent wrong HTTP response code: " + responseCode);
                }
                connection.helidonSocket = PlainSocket.client(connection.socket, connection.channelId);
            }
        },
        PROXY_HTTPS {
            @Override
            protected void connect(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException {
                UriHelper uri = UriHelper.create();
                uri.scheme("http");
                uri.host(remoteAddress.getHostName());
                uri.port(remoteAddress.getPort());
                InetSocketAddress proxyAddress = connection.connectionKey.proxy().address(uri).get();
                int responseCode = connection.connectToProxy(remoteAddress, proxyAddress);
                if (responseCode != Status.OK_200.code()) {
                    throw new IllegalStateException("Proxy sent wrong HTTP response code: " + responseCode);
                }
                SSLSocket sslSocket = connection.connectionKey.tls().createSocket("http/1.1", connection.socket, remoteAddress);
                connection.socket = sslSocket;
                sslSocket.startHandshake();
                connection.helidonSocket = TlsSocket.client(sslSocket, connection.channelId);
                if (LOGGER.isLoggable(TRACE)) {
                    connection.debugTls(sslSocket);
                }
            }
        };

        protected abstract void connect(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException;

        static ConnectionStrategy get(Http1ClientConnection connection, InetSocketAddress remoteAddress) {
            Proxy proxy = connection.connectionKey.proxy();
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
                return connection.connectionKey.tls() != null ? ConnectionStrategy.PROXY_HTTPS : ConnectionStrategy.PROXY_PLAIN;
            } else {
                return connection.connectionKey.tls() != null ? ConnectionStrategy.HTTPS : ConnectionStrategy.PLAIN;
            }
        }
    }
}

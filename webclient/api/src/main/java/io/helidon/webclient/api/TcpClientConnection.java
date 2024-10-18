/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.TlsSocket;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.spi.DnsResolver;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * A TCP connection that can be used by any protocol that is based on TCP.
 * The connection supports proxying and is not attempting to cache anything.
 */
public class TcpClientConnection implements ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(TcpClientConnection.class.getName());

    private final WebClient webClient;
    private final ConnectionKey connectionKey;
    private final List<String> tcpProtocolIds;
    private final Function<TcpClientConnection, Boolean> releaseFunction;
    private final Consumer<TcpClientConnection> closeConsumer;

    private String channelId;
    private Socket socket;
    private HelidonSocket helidonSocket;
    private DataReader reader;
    private DataWriter writer;
    private boolean closed;

    private TcpClientConnection(WebClient webClient,
                                ConnectionKey connectionKey,
                                List<String> tcpProtocolIds,
                                Function<TcpClientConnection, Boolean> releaseFunction,
                                Consumer<TcpClientConnection> closeConsumer) {
        this.webClient = webClient;
        this.connectionKey = connectionKey;
        this.tcpProtocolIds = tcpProtocolIds;
        this.releaseFunction = releaseFunction;
        this.closeConsumer = closeConsumer;
    }

    /**
     * Create a new TCP Connection.
     *
     * @param webClient       webclient, may be used to create proxy connections
     * @param connectionKey   connection key of the new connection (where and how to connect)
     * @param tcpProtocolIds  protocol IDs for ALPN (TLS protocol negotiation)
     * @param releaseFunction called when {@link #releaseResource()} is called, if {@code false} is returned, the connection will
     *                        be closed instead kept open
     * @param closeConsumer   called when {@link #closeResource()} is called, the connection is no longer usable after this moment
     * @return a new TCP connection, {@link #connect()} must be called to make it available for use
     */
    public static TcpClientConnection create(WebClient webClient,
                                             ConnectionKey connectionKey,
                                             List<String> tcpProtocolIds,
                                             Function<TcpClientConnection, Boolean> releaseFunction,
                                             Consumer<TcpClientConnection> closeConsumer) {
        return new TcpClientConnection(webClient, connectionKey, tcpProtocolIds, releaseFunction, closeConsumer);
    }

    /**
     * Connect this connection over the network.
     * This will resolve proxy connection, TLS negotiation (including ALPN) and return a connected connection.
     *
     * @return this connection, connected to the remote socket
     */
    public TcpClientConnection connect() {
        Tls tls = connectionKey.tls();
        InetSocketAddress targetAddress = inetSocketAddress();

        /*
        Obtain target socket through proxy (if enabled), or connect to target socket
         */
        this.socket = connectionKey.proxy()
                .tcpSocket(webClient,
                           targetAddress,
                           webClient.prototype().socketOptions(),
                           tls.enabled());

        this.channelId = createChannelId(socket);

        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, String.format("[client %s] client connected %s:%d %s",
                                            channelId,
                                            socket.getLocalAddress().getHostAddress(),
                                            socket.getLocalPort(),
                                            Thread.currentThread().getName()));
        }

        if (tls.enabled()) {
            SSLSocket sslSocket = tls.createSocket(tcpProtocolIds, socket, targetAddress);
            try {
                sslSocket.startHandshake();
            } catch (IOException e) {
                try {
                    sslSocket.close();
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
                throw new UncheckedIOException("Failed to execute SSL handshake", e);
            }
            if (LOGGER.isLoggable(TRACE)) {
                debugTls(sslSocket);
            }
            this.helidonSocket = TlsSocket.client(sslSocket, channelId);
        } else {
            this.helidonSocket = PlainSocket.client(socket, channelId);
        }
        this.reader = new DataReader(helidonSocket);
        this.writer = new DirectDatatWriter(helidonSocket);

        return this;
    }

    @Override
    public DataReader reader() {
        if (closed) {
            throw new IllegalStateException("Attempt to call reader() on a closed connection");
        }

        if (reader == null) {
            throw new IllegalStateException("Attempt to call reader() on a connection that is not connected");
        }

        return reader;
    }

    @Override
    public DataWriter writer() {
        if (closed) {
            throw new IllegalStateException("Attempt to call writer() on a closed connection");
        }

        if (writer == null) {
            throw new IllegalStateException("Attempt to call writer() on a connection that is not connected");
        }

        return writer;
    }

    @Override
    public void releaseResource() {
        if (closed) {
            return;
        }
        if (!releaseFunction.apply(this)) {
            closeResource();
        }
    }

    @Override
    public void closeResource() {
        if (closed) {
            return;
        }
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                LOGGER.log(TRACE, "Failed to close a client socket", e);
            }
        }
        this.closed = true;
        closeConsumer.accept(this);
    }

    @Override
    public String channelId() {
        if (channelId == null) {
            return "not-connected";
        }
        return channelId;
    }

    @Override
    public void readTimeout(Duration readTimeout) {
        if (closed) {
            throw new IllegalStateException("Attempt to call readTimeout(Duration) on a closed connection");
        }

        if (socket == null) {
            throw new IllegalStateException("Attempt to call readTimeout(Duration) on a connection that is not connected");
        }

        try {
            socket.setSoTimeout((int) readTimeout.toMillis());
        } catch (SocketException e) {
            throw new UncheckedIOException("Could not set read timeout to the connection with the channel id: " + channelId, e);
        }
    }

    @Override
    public HelidonSocket helidonSocket() {
        return helidonSocket;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && helidonSocket().isConnected();
    }

    Socket socket() {
        return socket;
    }

    private String createChannelId(Socket socket) {
        return "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
    }

    private InetSocketAddress inetSocketAddress() {
        DnsResolver dnsResolver = connectionKey.dnsResolver();
        InetAddress address = dnsResolver.resolveAddress(connectionKey.host(), connectionKey.dnsAddressLookup());
        return new InetSocketAddress(address, connectionKey.port());
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

    private static class DirectDatatWriter implements DataWriter {
        private final HelidonSocket helidonSocket;

        DirectDatatWriter(HelidonSocket helidonSocket) {
            this.helidonSocket = helidonSocket;
        }

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
    }
}

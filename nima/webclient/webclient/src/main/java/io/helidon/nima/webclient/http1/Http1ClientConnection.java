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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Queue;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.socket.TlsSocket;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.ConnectionKey;
import io.helidon.nima.webclient.spi.DnsResolver;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

class Http1ClientConnection implements ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientConnection.class.getName());

    private final Queue<Http1ClientConnection> connectionQueue;
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
                          Queue<Http1ClientConnection> connectionQueue,
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

    private int proxyTunneling(InetSocketAddress remoteAddress) throws IOException {
        StringBuilder httpConnect = new StringBuilder();
        httpConnect.append("CONNECT ").append(remoteAddress.getHostName())
        .append(":").append(remoteAddress.getPort()).append(" HTTP/1.1").append("\r\n");
        httpConnect.append("Host: ").append(remoteAddress.getHostName())
        .append(":").append(remoteAddress.getPort()).append("\r\n");
//        httpConnect.append("Proxy-Connection: Keep-Alive").append("\r\n");
        httpConnect.append("Accept: */*").append("\r\n");
        if (connectionKey.proxy().username().isPresent()
                && connectionKey.proxy().password().isPresent()) {
            byte[] bytes = new StringBuilder().append(connectionKey.proxy().username().get())
                    .append(":").append(connectionKey.proxy().password().get()).toString().getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            httpConnect.append("Authorization: Basic ").append(base64).append("\r\n");
        }
        httpConnect.append("\r\n");
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, String.format("Proxy client connected %s %s",
                                            connectionKey.proxy().address(),
                                            Thread.currentThread().getName()));
        }
        socket.getOutputStream().write(httpConnect.toString().getBytes());
        socket.getOutputStream().flush();
        int read;
        byte[] buffer = new byte[1024];
        StringBuilder response = new StringBuilder();
        String firstLine = null;
        int responseCode = -1;
        while ((read = socket.getInputStream().read(buffer)) != -1) {
            String resp = new String(buffer, 0, read);
            if (firstLine == null) {
                firstLine = resp.split("\r\n")[0];
                responseCode = Integer.parseInt(firstLine.split(" ")[1]);
            }
            response.append(resp);
            if (resp.endsWith("\r\n\r\n")) {
                break;
            }
        }
        return responseCode;
    }

    Http1ClientConnection connect() {
        try {
            socket = new Socket();
            socket.setSoTimeout((int) options.readTimeout().toMillis());
            options.configureSocket(socket);
            InetSocketAddress remoteAddress = inetSocketAddress();
            EstablishConnection.configure(this, remoteAddress);
            channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not connect to " + connectionKey.host() + ":" + connectionKey.port(), e);
        }

        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, String.format("[%s] client connected %s %s",
                                            channelId,
                                            socket.getLocalAddress(),
                                            Thread.currentThread().getName()));
        }

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
            if (connectionQueue.offer(this)) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, String.format("[%s] client connection returned %s",
                                                    channelId,
                                                    Thread.currentThread().getName()));
                }
                return;
            }
        }
        this.release();
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

    private static enum EstablishConnection {
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
                connection.socket.connect(connection.connectionKey.proxy().address(), (int) connection.options.connectTimeout().toMillis());
                int responseCode = connection.proxyTunneling(remoteAddress);
                if (responseCode != 200) {
                    throw new IllegalStateException("Proxy sent wrong HTTP response code: " + responseCode);
                }
                connection.helidonSocket = PlainSocket.client(connection.socket, connection.channelId);
            }
        },
        PROXY_HTTPS {
            @Override
            protected void connect(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException {
                connection.socket.connect(connection.connectionKey.proxy().address(), (int) connection.options.connectTimeout().toMillis());
                int responseCode = connection.proxyTunneling(remoteAddress);
                if (responseCode != 200) {
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

        static void configure(Http1ClientConnection connection, InetSocketAddress remoteAddress) throws IOException {
            EstablishConnection connector = null;
            if (connection.connectionKey.proxy() != null) {
                connector = connection.connectionKey.tls() != null ? PROXY_HTTPS : PROXY_PLAIN;
            } else {
                connector = connection.connectionKey.tls() != null ? HTTPS : PLAIN;
            }
            connector.connect(connection, remoteAddress);
        }
    }
}

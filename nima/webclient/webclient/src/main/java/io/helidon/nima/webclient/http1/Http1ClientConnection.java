/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
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
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.ClientConnection;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

class Http1ClientConnection implements ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientConnection.class.getName());

    private final Queue<Http1ClientConnection> connectionQueue;
    private final String host;
    private final int port;
    private final Tls tls;
    private final io.helidon.common.socket.SocketOptions options;
    private String channelId;
    private boolean keepAlive;
    private Socket socket;
    private HelidonSocket helidonSocket;
    private DataReader reader;
    private DataWriter writer;

    Http1ClientConnection(SocketOptions options, String host, int port, Tls tls) {
        this(options, null, host, port, tls);
    }

    Http1ClientConnection(SocketOptions options,
                          Queue<Http1ClientConnection> connectionQueue,
                          String host,
                          int port,
                          Tls tls) {
        this.options = options;
        this.connectionQueue = connectionQueue;
        this.host = host;
        this.port = port;
        this.keepAlive = (connectionQueue != null);
        this.tls = tls;
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

    Http1ClientConnection connect() {
        try {
            SSLSocket sslSocket = tls == null ? null : tls.createSocket("http/1.1");

            socket = sslSocket == null ? new Socket() : sslSocket;
            socket.setSoTimeout((int) options.readTimeout().toMillis());
            options.configureSocket(socket);
            socket.connect(new InetSocketAddress(host, port),
                           (int) options.connectTimeout().toMillis());

            channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));

            if (sslSocket == null) {
                helidonSocket = PlainSocket.client(socket, channelId);
            } else {
                sslSocket.startHandshake();
                helidonSocket = TlsSocket.client(sslSocket, channelId);
                if (LOGGER.isLoggable(TRACE)) {
                    debugTls(sslSocket);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not connect to " + host + ":" + port, e);
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
}

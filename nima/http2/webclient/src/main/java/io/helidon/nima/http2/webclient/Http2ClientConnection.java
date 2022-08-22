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

package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.TlsSocket;
import io.helidon.nima.http2.Http2ConnectionWriter;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

class Http2ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http2ClientConnection.class.getName());

    private final ExecutorService executor;
    private final SocketOptions socketOptions;
    private final ConnectionKey connectionKey;
    private final boolean priorKnowledge;

    private String channelId;
    private Socket socket;
    private PlainSocket helidonSocket;
    private Http2ConnectionWriter writer;
    private InputStream inputStream;

    Http2ClientConnection(ExecutorService executor,
                          SocketOptions socketOptions,
                          ConnectionKey connectionKey,
                          boolean priorKnowledge) {
        this.executor = executor;
        this.socketOptions = socketOptions;
        this.connectionKey = connectionKey;
        this.priorKnowledge = priorKnowledge;
    }

    Http2ClientConnection connect() {
        try {
            doConnect();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not connect to " + connectionKey + ", with options: " + socketOptions, e);
        }

        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, String.format("[%s] client connected %s %s",
                                            channelId,
                                            socket.getLocalAddress(),
                                            Thread.currentThread().getName()));
        }

        return this;
    }

    Http2ClientStream stream(int priority) {
        return null;
    }

    Http2ClientStream tryStream(int priority) {
        try {
            return stream(priority);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doConnect() throws IOException {
        SSLSocket sslSocket = connectionKey.tls() == null
                ? null
                : connectionKey.tls().createSocket("h2");
        socket = sslSocket == null ? new Socket() : sslSocket;

        socketOptions.configureSocket(socket);
        socket.connect(new InetSocketAddress(connectionKey.host(),
                                             connectionKey.port()),
                       (int) socketOptions.connectTimeout().toMillis());
        channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));

        helidonSocket = sslSocket == null
                ? PlainSocket.client(socket, channelId)
                : TlsSocket.client(sslSocket, channelId);

        DataWriter writer = new SocketWriter(executor, helidonSocket, channelId, 32);
        inputStream = socket.getInputStream();
        this.writer = new Http2ConnectionWriter(helidonSocket, writer, List.of());

        if (sslSocket != null) {
            sslSocket.startHandshake();
            if (LOGGER.isLoggable(TRACE)) {
                debugTls(sslSocket);
            }
            String negotiatedProtocol = sslSocket.getApplicationProtocol();
            if (!"h2".equals(negotiatedProtocol)) {
                close();
                throw new IllegalStateException("Failed to negotiate h2 protocol. Protocol from socket: " + negotiatedProtocol);
            }
        }
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

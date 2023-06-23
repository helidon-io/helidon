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

package io.helidon.nima.websocket.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Random;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.TlsSocket;
import io.helidon.nima.webclient.LoomClient;
import io.helidon.nima.webclient.http1.Http1StatusParser;
import io.helidon.nima.webclient.spi.DnsResolver;
import io.helidon.nima.websocket.WsListener;

import static java.lang.System.Logger.Level.TRACE;

class WsClientImpl extends LoomClient implements WsClient {
    private static final System.Logger LOGGER = System.getLogger(WsClient.class.getName());
    private static final Http.HeaderValue HEADER_CONN_UPGRADE = Header.create(Header.CONNECTION, "Upgrade");
    private static final Http.HeaderName HEADER_WS_ACCEPT = Header.create("Sec-WebSocket-Accept");
    private static final Http.HeaderName HEADER_WS_KEY = Header.create("Sec-WebSocket-Key");
    private static final LazyValue<Random> RANDOM = LazyValue.create(SecureRandom::new);
    private static final byte[] KEY_SUFFIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII);
    private static final int KEY_SUFFIX_LENGTH = KEY_SUFFIX.length;
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();

    protected WsClientImpl(WsClient.Builder builder) {
        super(builder);
    }

    @Override
    public void connect(URI uri, WsListener listener) {
        // there is no connection pooling, as each connection is upgraded to be a websocket connection
        Socket socket;
        SSLSocket sslSocket = tls().enabled() ? null : tls().createSocket("http");
        socket = sslSocket == null ? new Socket() : sslSocket;

        socketOptions().configureSocket(socket);
        DnsResolver dnsResolver = dnsResolver();
        try {
            if (dnsResolver.useDefaultJavaResolver()) {
                socket.connect(new InetSocketAddress(uri.getHost(), uri().getPort()),
                               (int) socketOptions().connectTimeout().toMillis());
            } else {
                InetAddress address = dnsResolver.resolveAddress(uri.getHost(), dnsAddressLookup());
                socket.connect(new InetSocketAddress(address, uri.getPort()), (int) socketOptions().connectTimeout().toMillis());
            }
        } catch (Exception e) {
            throw new WsClientException("Failed to connect to remote server on " + uri, e);
        }
        String channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
        HelidonSocket helidonSocket = sslSocket == null
                ? PlainSocket.client(socket, channelId)
                : TlsSocket.client(sslSocket, channelId);

        try {
            finishConnect(channelId, helidonSocket, sslSocket, uri, listener);
        } catch (Exception e) {
            try {
                helidonSocket.close();
            } catch (Exception ex) {
                ex.addSuppressed(e);
                throw ex;
            }
            throw e;
        }
    }

    @Override
    public void connect(String path, WsListener listener) {
        URI baseUri = super.uri();
        if (baseUri == null) {
            connect(URI.create(path), listener);
        } else {
            connect(baseUri.resolve(path), listener);
        }
    }

    protected String hash(SocketContext ctx, String wsKey) {
        byte[] wsKeyBytes = wsKey.getBytes(StandardCharsets.US_ASCII);
        int wsKeyBytesLength = wsKeyBytes.length;
        byte[] toHash = new byte[wsKeyBytesLength + KEY_SUFFIX_LENGTH];
        System.arraycopy(wsKeyBytes, 0, toHash, 0, wsKeyBytesLength);
        System.arraycopy(KEY_SUFFIX, 0, toHash, wsKeyBytesLength, KEY_SUFFIX_LENGTH);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
            return B64_ENCODER.encodeToString(digest.digest(toHash));
        } catch (NoSuchAlgorithmException e) {
            ctx.log(LOGGER, System.Logger.Level.ERROR, "SHA-1 must be provided for WebSocket to work", e);
            throw new IllegalStateException("SHA-1 not provided", e);
        }
    }

    private void finishConnect(String channelId, HelidonSocket helidonSocket, SSLSocket sslSocket, URI uri, WsListener listener) {
        DataWriter writer = SocketWriter.create(executor(), helidonSocket, 0);

        if (sslSocket != null) {
            try {
                sslSocket.startHandshake();
            } catch (IOException e) {
                throw new WsClientException("Failed to do SSL handshake", e);
            }
            if (LOGGER.isLoggable(TRACE)) {
                debugTls(sslSocket, channelId);
            }
            String negotiatedProtocol = sslSocket.getApplicationProtocol();
            if (negotiatedProtocol != null && !"http".equals(negotiatedProtocol)) {
                helidonSocket.close();
                throw new IllegalStateException("Failed to negotiate http protocol. Protocol from socket: " + negotiatedProtocol);
            }
        }

        // TLS negotiated, socket connected - now upgrade (GET request to the correct path)
        /*
        GET path HTTP/1.1
        Upgrade: websocket
        Sec-WebSocket-Extensions: list of extensions
        Sec-WebSocket-Protocol: subprotocols
        Sec-WebSocket-Key: computed value
        Sec-WebSocket-Version: 13
         */

        /*
        Prepare prologue
         */
        String prologue = "GET " + uri.getPath() + " HTTP/1.1\r\n";
        /*
        Prepare headers
         */
        WritableHeaders<?> headers = WritableHeaders.create(defaultHeaders());
        byte[] nonce = new byte[16];
        RANDOM.get().nextBytes(nonce);
        String secWsKey = B64_ENCODER.encodeToString(nonce);
        headers.set(HEADER_WS_KEY, secWsKey);
        headers.setIfAbsent(Header.create(Header.HOST, uri.getRawAuthority()));

        BufferData data = BufferData.growing(512);
        data.writeAscii(prologue);
        for (Http.HeaderValue header : headers) {
            header.writeHttp1Header(data);
        }
        // end of headers - write CRLF (also end of request)
        data.write(Bytes.CR_BYTE);
        data.write(Bytes.LF_BYTE);
        writer.write(data);

        // we have written a full upgrade request, now let's wait for response and make sure it is a valid WS upgrade
        // response
        /*
        HTTP/1.1 101 Switching Protocols
        Connection: Upgrade
        Upgrade: websocket
        Sec-WebSocket-Accept: ...
         */
        DataReader reader = new DataReader(helidonSocket);
        Http.Status status = Http1StatusParser.readStatus(reader, 256);
        if (!status.equals(Http.Status.SWITCHING_PROTOCOLS_101)) {
            throw new WsClientException("Failed to upgrade to WebSocket, expected switching protocols status, but got: "
                                                + status);
        }
        WritableHeaders<?> responseHeaders = Http1HeadersParser.readHeaders(reader, 4096, true);
        if (!responseHeaders.contains(HEADER_CONN_UPGRADE)) {
            throw new WsClientException("Failed to upgrade to WebSocket, expected Connection: Upgrade header. Headers: "
                                                + responseHeaders);
        }
        if (!responseHeaders.contains(WsClient.Builder.HEADER_UPGRADE_WS)) {
            throw new WsClientException("Failed to upgrade to WebSocket, expected Upgrade: websocket header. Headers: "
                                                + responseHeaders);
        }
        if (!responseHeaders.contains(HEADER_WS_ACCEPT)) {
            throw new WsClientException("Failed to upgrade to WebSocket, expected Sec-WebSocket-Accept header. Headers: "
                                                + responseHeaders);
        }
        String secWsAccept = responseHeaders.get(HEADER_WS_ACCEPT).value();
        if (!hash(helidonSocket, secWsKey).equals(secWsAccept)) {
            throw new WsClientException("Failed to upgrade to WebSocket, expected valid secWsKey. Headers: "
                                                + responseHeaders);
        }

        // we are upgraded, there is no entity, we can switch to web socket
        ClientWsConnection session;

        // sub-protocol exists
        if (headers.contains(WsClient.Builder.HEADER_WS_PROTOCOL)) {
            session = new ClientWsConnection(listener,
                                             helidonSocket,
                                             reader,
                                             writer,
                                             Optional.of(headers.get(WsClient.Builder.HEADER_WS_PROTOCOL).value()));
        } else {
            session = new ClientWsConnection(listener, helidonSocket, reader, writer, Optional.empty());
        }
        // we have connected, now (as we give control to socket listener), we need to run on a separate thread
        executor().submit(session);
    }

    private void debugTls(SSLSocket sslSocket, String channelId) {
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

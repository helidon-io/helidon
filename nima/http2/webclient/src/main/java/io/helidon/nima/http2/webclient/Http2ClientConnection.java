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

package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.TlsSocket;
import io.helidon.nima.http2.ConnectionFlowControl;
import io.helidon.nima.http2.Http2ConnectionWriter;
import io.helidon.nima.http2.Http2ErrorCode;
import io.helidon.nima.http2.Http2Exception;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameListener;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2GoAway;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2LoggingFrameListener;
import io.helidon.nima.http2.Http2Ping;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.WindowSize;
import io.helidon.nima.webclient.spi.DnsResolver;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

class Http2ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http2ClientConnection.class.getName());

    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");

    private static final int FRAME_HEADER_LENGTH = 9;

    private static final String UPGRADE_REQ_MASK = """
            %s %s HTTP/1.1\r
            Host: %s:%s\r
            Connection: Upgrade, HTTP2-Settings\r
            Upgrade: h2c\r
            HTTP2-Settings: %s\r\n\r
            """;
    private static final byte[] PRIOR_KNOWLEDGE_PREFACE =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    private final ExecutorService executor;
    private final SocketOptions socketOptions;
    private final ConnectionKey connectionKey;
    private final String primaryPath;
    private final boolean priorKnowledge;
    private final LockingStreamIdSequence streamIdSeq = new LockingStreamIdSequence();
    private final Map<Integer, Queue<Http2FrameData>> buffer = new HashMap<>();
    private final Map<Integer, Http2ClientStream> streams = new HashMap<>();
    private final Lock connectionLock = new ReentrantLock();
    private final Semaphore dequeSemaphore = new Semaphore(1);
    private final ConnectionFlowControl connectionFlowControl;

    private Http2Settings serverSettings = Http2Settings.builder()
            .build();

    private String channelId;
    private Socket socket;
    private PlainSocket helidonSocket;
    private Http2ConnectionWriter writer;
    private DataReader reader;
    private DataWriter dataWriter;
    private final Http2Headers.DynamicTable inboundDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private Future<?> handleTask;

    Http2ClientConnection(ExecutorService executor,
                          SocketOptions socketOptions,
                          ConnectionKey connectionKey,
                          String primaryPath,
                          boolean priorKnowledge) {
        this.executor = executor;
        this.socketOptions = socketOptions;
        this.connectionKey = connectionKey;
        this.primaryPath = primaryPath;
        this.priorKnowledge = priorKnowledge;
        this.connectionFlowControl = ConnectionFlowControl.createClient(this::writeWindowsUpdate);
    }

    private void writeWindowsUpdate(int streamId, Http2WindowUpdate windowUpdateFrame) {
        writer.write(windowUpdateFrame.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
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

    private Queue<Http2FrameData> buffer(int streamId) {
        return buffer.computeIfAbsent(streamId, i -> new ArrayDeque<>());
    }

    Http2FrameData readNextFrame(int streamId) {
        try {
            // Block deque thread when queue is empty
            dequeSemaphore.acquire();
            connectionLock.lock();
            return buffer(streamId).poll();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            connectionLock.unlock();
        }
    }

    private void handle() {
        this.reader.ensureAvailable();
        BufferData frameHeaderBuffer = this.reader.readBuffer(FRAME_HEADER_LENGTH);
        Http2FrameHeader frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
        frameHeader.type().checkLength(frameHeader.length());
        BufferData data;
        if (frameHeader.length() != 0) {
            data = this.reader.readBuffer(frameHeader.length());
        } else {
            data = BufferData.empty();
        }

        int streamId = frameHeader.streamId();

        switch (frameHeader.type()) {
        case GO_AWAY:
            Http2GoAway http2GoAway = Http2GoAway.create(data);
            recvListener.frameHeader(helidonSocket, frameHeader);
            recvListener.frame(helidonSocket, http2GoAway);
            this.close();
            throw new IllegalStateException("Connection closed by the other side, error code: "
                                                    + http2GoAway.errorCode()
                                                    + " lastStreamId: " + http2GoAway.lastStreamId());

        case SETTINGS:
            serverSettings = Http2Settings.create(data);
            recvListener.frameHeader(helidonSocket, frameHeader);
            recvListener.frame(helidonSocket, serverSettings);
            // §4.3.1 Endpoint communicates the size chosen by its HPACK decoder context
            inboundDynamicTable.protocolMaxTableSize(serverSettings.value(Http2Setting.HEADER_TABLE_SIZE));
            if (serverSettings.hasValue(Http2Setting.MAX_FRAME_SIZE)) {
                connectionFlowControl.resetMaxFrameSize(serverSettings.value(Http2Setting.MAX_FRAME_SIZE).intValue());
            }
            // §6.5.2 Update initial window size for new streams and window sizes of all already existing streams
            if (serverSettings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE)) {
                Long initWinSizeLong = serverSettings.value(Http2Setting.INITIAL_WINDOW_SIZE);
                if (initWinSizeLong > WindowSize.MAX_WIN_SIZE) {
                    goAway(streamId, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
                    throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                             "Received too big INITIAL_WINDOW_SIZE " + initWinSizeLong);
                }
                int initWinSize = initWinSizeLong.intValue();
                connectionFlowControl.resetInitialWindowSize(initWinSize);
                streams.values().forEach(stream -> stream.flowControl().outbound().resetStreamWindowSize(initWinSize));

            }
            // §6.5.3 Settings Synchronization
            ackSettings();
            //FIXME: Other settings
            return;

        case WINDOW_UPDATE:
            Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(data);
            recvListener.frameHeader(helidonSocket, frameHeader);
            recvListener.frame(helidonSocket, windowUpdate);
            // Outbound flow-control window update
            if (streamId == 0) {
                int increment = windowUpdate.windowSizeIncrement();
                boolean overflow;
                // overall connection
                if (increment == 0) {
                    Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "Window size 0");
                    writer.write(frame.toFrameData(serverSettings, 0, Http2Flag.NoFlags.create()));
                }
                overflow = connectionFlowControl.incrementOutboundConnectionWindowSize(increment) > WindowSize.MAX_WIN_SIZE;
                if (overflow) {
                    Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
                    writer.write(frame.toFrameData(serverSettings, 0, Http2Flag.NoFlags.create()));
                }


            } else {
                streams.get(streamId)
                        .windowUpdate(windowUpdate);
            }
            return;
        case PING:
            if (streamId != 0) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                         "Received ping for a stream " + streamId);
            }
            if (frameHeader.length() != 8) {
                throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                         "Received ping with wrong size. Should be 8 bytes, is " + frameHeader.length());
            }
            if (!frameHeader.flags(Http2FrameTypes.PING).ack()) {
                Http2Ping ping = Http2Ping.create(data);
                recvListener.frame(helidonSocket, ping);
                BufferData frame = ping.data();
                Http2FrameHeader header = Http2FrameHeader.create(frame.available(),
                                                                  Http2FrameTypes.PING,
                                                                  Http2Flag.PingFlags.create(Http2Flag.ACK),
                                                                  0);
                writer.write(new Http2FrameData(header, frame));
            }
            break;

        case RST_STREAM:
            Http2RstStream rstStream = Http2RstStream.create(data);
            recvListener.frame(helidonSocket, rstStream);
            stream(streamId).rstStream(rstStream);
            break;

        case DATA:
            stream(streamId).flowControl().inbound().decrementWindowSize(frameHeader.length());
            enqueue(streamId, new Http2FrameData(frameHeader, data));
            break;

        case HEADERS:
            enqueue(streamId, new Http2FrameData(frameHeader, data));
            return;

        case CONTINUATION:
            //FIXME: Header continuation
            throw new UnsupportedOperationException("Continuation support is not implemented yet!");

        default:
            LOGGER.log(WARNING, "Unsupported frame type!! " + frameHeader.type());
        }

    }

    Http2ClientStream stream(int streamId) {
        return streams.get(streamId);
    }

    Http2ClientStream createStream(int priority) {
        //FIXME: priority
        return new Http2ClientStream(this,
                                     serverSettings,
                                     helidonSocket,
                                     streamIdSeq);
    }

    void addStream(int streamId, Http2ClientStream stream){
        this.streams.put(streamId, stream);
    }

    Http2ClientStream tryStream(int priority) {
        try {
            return createStream(priority);
        } catch (IllegalStateException | UncheckedIOException e) {
            return null;
        }
    }

    void close() {
        try {
            handleTask.cancel(true);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void enqueue(int streamId, Http2FrameData frameData){
        try {
            connectionLock.lock();
            buffer(streamId).add(frameData);
        } finally {
            connectionLock.unlock();
            // Release deque threads
            dequeSemaphore.release();
        }
    }

    private void doConnect() throws IOException {
        boolean useTls = "https".equals(connectionKey.scheme()) && connectionKey.tls() != null;

        SSLSocket sslSocket = useTls ? connectionKey.tls().createSocket("h2") : null;
        socket = sslSocket == null ? new Socket() : sslSocket;

        socketOptions.configureSocket(socket);
        DnsResolver dnsResolver = connectionKey.dnsResolver();
        if (dnsResolver.useDefaultJavaResolver()) {
            socket.connect(new InetSocketAddress(connectionKey.host(), connectionKey.port()),
                           (int) socketOptions.connectTimeout().toMillis());
        } else {
            InetAddress address = dnsResolver.resolveAddress(connectionKey.host(), connectionKey.dnsAddressLookup());
            socket.connect(new InetSocketAddress(address, connectionKey.port()), (int) socketOptions.connectTimeout().toMillis());
        }
        channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));

        helidonSocket = sslSocket == null
                ? PlainSocket.client(socket, channelId)
                : TlsSocket.client(sslSocket, channelId);

        dataWriter = SocketWriter.create(executor, helidonSocket, 32);
        this.reader = new DataReader(helidonSocket);
        this.writer = new Http2ConnectionWriter(helidonSocket, dataWriter, List.of());

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

        if (!priorKnowledge && !useTls) {
            httpUpgrade();
            // Settings are part of the HTTP/1 upgrade request
            sendPreface(false);
        } else {
            sendPreface(true);
        }

        handleTask = executor.submit(() -> {
            while (!Thread.interrupted()) {
                handle();
            }
            System.out.println("Client listener interrupted!!!");
        });
    }

    private void ackSettings() {
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(Http2Flag.ACK);
        Http2Settings http2Settings = Http2Settings.create();
        Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
        sendListener.frameHeader(helidonSocket, frameData.header());
        sendListener.frame(helidonSocket, http2Settings);
        writer.write(frameData);
    }

    private void goAway(int streamId, Http2ErrorCode errorCode, String msg) {
        Http2Settings http2Settings = Http2Settings.create();
        Http2GoAway frame = new Http2GoAway(streamId, errorCode, msg);
        writer.write(frame.toFrameData(http2Settings, 0, Http2Flag.NoFlags.create()));
    }

    private void sendPreface(boolean sendSettings){
        dataWriter.writeNow(BufferData.create(PRIOR_KNOWLEDGE_PREFACE));
        if (sendSettings) {
            // §3.5 Preface bytes must be followed by setting frame
            Http2Settings http2Settings = Http2Settings.builder()
                    .add(Http2Setting.MAX_HEADER_LIST_SIZE, 8192L)
                    .add(Http2Setting.ENABLE_PUSH, false)
                   // .add(Http2Setting., false)
                    .build();
            Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(0);
            Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
            sendListener.frameHeader(helidonSocket, frameData.header());
            sendListener.frame(helidonSocket, http2Settings);
            writer.write(frameData);
        }
        // win update
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(10000); //FIXME: configurable
        Http2Flag.NoFlags flags = Http2Flag.NoFlags.create();
        Http2FrameData frameData = windowUpdate.toFrameData(null, 0, flags);
        sendListener.frameHeader(helidonSocket, frameData.header());
        sendListener.frame(helidonSocket, windowUpdate);
        writer.write(frameData);
    }

    private void httpUpgrade() {
        Http2FrameData settingsFrame = Http2Settings.create().toFrameData(null, 0, Http2Flag.SettingsFlags.create(0));
        BufferData upgradeRequest = createUpgradeRequest(settingsFrame);
        sendListener.frame(helidonSocket, upgradeRequest);
        dataWriter.writeNow(upgradeRequest);
        reader.skip("HTTP/1.1 ".length());
        String status = reader.readAsciiString(3);
        String message = reader.readLine();
        WritableHeaders<?> headers = Http1HeadersParser.readHeaders(reader, 8192, false);
        switch (status) {
            case "101":
                break;
            case "301":
                throw new UpgradeRedirectException(headers.get(Http.Header.LOCATION).value());
            default:
                close();
                throw new IllegalStateException("Upgrade to HTTP/2 failed: " + message);
        }
    }

    private BufferData createUpgradeRequest(Http2FrameData settingsFrame) {
        BufferData settingsData = settingsFrame.data();
        byte[] b = new byte[settingsData.available()];
        settingsData.write(b);

        return BufferData.create(UPGRADE_REQ_MASK.formatted(
                connectionKey.method().text(),
                this.primaryPath != null && !"".equals(this.primaryPath) ? primaryPath : "/",
                connectionKey.host(),
                connectionKey.port(),
                Base64.getEncoder().encodeToString(b)
        ));
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

    public Http2ConnectionWriter getWriter() {
        return writer;
    }

    public Http2Headers.DynamicTable getInboundDynamicTable() {
        return this.inboundDynamicTable;
    }

    ConnectionFlowControl flowControl(){
        return this.connectionFlowControl;
    }
}

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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.websocket.ClientWsFrame;
import io.helidon.nima.websocket.ServerWsFrame;
import io.helidon.nima.websocket.WsCloseCodes;
import io.helidon.nima.websocket.WsCloseException;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsOpCode;
import io.helidon.nima.websocket.WsSession;

/**
 * Client WebSocket connection. This connection handles a single WebSocket interaction, using
 * {@link io.helidon.nima.websocket.WsListener} to handle connection events.
 */
public class ClientWsConnection implements WsSession, Runnable {
    private static final System.Logger LOGGER = System.getLogger(ClientWsConnection.class.getName());

    private final WsListener listener;
    private final String subProtocol;
    private final BufferData sendBuffer = BufferData.growing(1024);
    private final ClientConnection connection;
    private final HelidonSocket helidonSocket;

    private ContinuationType recvContinuation = ContinuationType.NONE;
    private boolean sendContinuation;
    private boolean closeSent;
    private boolean terminated;

    ClientWsConnection(ClientConnection connection,
                       WsListener listener,
                       String subProtocol) {
        this.connection = connection;
        this.listener = listener;
        this.subProtocol = subProtocol;
        this.helidonSocket = connection.helidonSocket();
    }

    ClientWsConnection(ClientConnection connection,
                       WsListener listener) {
        this(connection, listener, null);
    }

    /**
     * Create a new connection. The connection needs to run on ana executor service (it implements {@link java.lang.Runnable})
     * so it does not block the current thread.
     *
     * @param clientConnection connection to use for this WS connection
     * @param listener         WebSocket listener to handle events on this connection
     * @param subProtocol      chosen sub-protocol of this connection (negotiated during upgrade from HTTP/1)
     * @return a new WebSocket connection
     */
    public static ClientWsConnection create(ClientConnection clientConnection,
                                            WsListener listener,
                                            String subProtocol) {
        return new ClientWsConnection(clientConnection, listener, subProtocol);
    }

    /**
     * Create a new connection without a sub-protocol.
     *
     * @param clientConnection connection to work on
     * @param listener         WebSocket listener to handle events on this connection
     * @return a new WebSocket connection
     */
    public static ClientWsConnection create(ClientConnection clientConnection,
                                            WsListener listener) {
        return new ClientWsConnection(clientConnection, listener);
    }

    @Override
    public void run() {
        Thread.currentThread().setName(connection.channelId() + " ws client");
        try {
            doRun();
        } catch (Exception e) {
            try {
                listener.onError(this, e);
                this.close(WsCloseCodes.UNEXPECTED_CONDITION, e.getMessage());
            } catch (Exception ex) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    ex.addSuppressed(e);
                    LOGGER.log(System.Logger.Level.TRACE, "Exception while handling exception.", ex);
                }
            }
        } finally {
            connection.closeResource();
        }
    }

    @Override
    public WsSession send(String text, boolean last) {
        return send(ClientWsFrame.data(text, last));
    }

    @Override
    public WsSession send(BufferData bufferData, boolean last) {
        return send(ClientWsFrame.data(bufferData, last));
    }

    @Override
    public WsSession ping(BufferData bufferData) {
        return send(ClientWsFrame.control(WsOpCode.PING, bufferData));
    }

    @Override
    public WsSession pong(BufferData bufferData) {
        return send(ClientWsFrame.control(WsOpCode.PONG, bufferData));
    }

    @Override
    public WsSession close(int code, String reason) {
        closeSent = true;

        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(2 + reasonBytes.length);
        bufferData.writeInt16(code);
        bufferData.write(reasonBytes);
        send(ClientWsFrame.control(WsOpCode.CLOSE, bufferData));

        return this;
    }

    @Override
    public WsSession terminate() {
        terminated = true;
        close(WsCloseCodes.NORMAL_CLOSE, "Terminate");

        return this;
    }

    @Override
    public Optional<String> subProtocol() {
        return Optional.ofNullable(subProtocol);
    }

    private ClientWsConnection send(ClientWsFrame frame) {
        WsOpCode opCode = frame.opCode();
        if (opCode == WsOpCode.TEXT || opCode == WsOpCode.BINARY) {
            if (sendContinuation) {
                opCode = WsOpCode.CONTINUATION;
            }
            sendContinuation = !frame.fin();
        }
        frame.opCode(opCode);

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            helidonSocket.log(LOGGER, System.Logger.Level.TRACE, "ws client frame send %s", frame);
        }

        sendBuffer.clear();
        int opCodeFull = frame.fin() ? 0b10000000 : 0;
        opCodeFull |= opCode.code();
        sendBuffer.write(opCodeFull);

        long payloadLength = frame.payloadLength();
        if (frame.payloadLength() < 126) {
            // this is a masked frame (all client frames MUST be masked)
            payloadLength = payloadLength | 0b10000000;
            sendBuffer.write((int) payloadLength);
            // TODO finish other options (payload longer than 126 bytes)
        }

        // write masking key
        int[] maskingKey = frame.maskingKey();
        sendBuffer.write(maskingKey[0]);
        sendBuffer.write(maskingKey[1]);
        sendBuffer.write(maskingKey[2]);
        sendBuffer.write(maskingKey[3]);
        sendBuffer.write(frame.maskedData());
        connection.writer().writeNow(sendBuffer);
        return this;
    }

    private void doRun() {
        listener.onOpen(this);
        while (!terminated) {
            try {
                ServerWsFrame frame = readFrame();
                if (!processFrame(frame)) {
                    return;
                }
            } catch (DataReader.InsufficientDataAvailableException e) {
                return;
            } catch (WsCloseException e) {
                if (!closeSent) {
                    try {
                        close(e.closeCode(), e.getMessage());
                    } catch (Exception ex) {
                        // we may receive an exception if the remote site closed the connection already
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            helidonSocket.log(LOGGER,
                                              System.Logger.Level.DEBUG,
                                              "Failed to send close, remote probably closed connection",
                                              ex);
                        }
                    }
                }
            } catch (Exception e) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Failed while reading or processing frames", e);
                }
                return;
            }
        }
    }

    private boolean processFrame(ServerWsFrame frame) {
        BufferData payload = frame.payloadData();
        switch (frame.opCode()) {
        case CONTINUATION -> {
            boolean finalFrame = frame.fin();
            ContinuationType ct = recvContinuation;
            if (finalFrame) {
                recvContinuation = ContinuationType.NONE;
            }
            switch (ct) {
            case TEXT -> listener.onMessage(this, payload.readString(payload.available(), StandardCharsets.UTF_8), finalFrame);
            case BINARY -> listener.onMessage(this, payload, finalFrame);
            default -> {
                close(WsCloseCodes.PROTOCOL_ERROR, "Unexpected continuation received");
                throw new WsClientException("Unexpected continuation received");
            }
            }
        }
        case TEXT -> {
            recvContinuation = ContinuationType.TEXT;
            listener.onMessage(this, payload.readString(payload.available(), StandardCharsets.UTF_8), frame.fin());
        }
        case BINARY -> {
            recvContinuation = ContinuationType.BINARY;
            listener.onMessage(this, payload, frame.fin());
        }
        case CLOSE -> {
            int status = payload.readInt16();
            String reason;
            if (payload.available() > 0) {
                reason = payload.readString(payload.available(), StandardCharsets.UTF_8);
            } else {
                reason = "normal";
            }
            listener.onClose(this, status, reason);
            throw new WsCloseException("normal", WsCloseCodes.NORMAL_CLOSE);
        }
        case PING -> listener.onPing(this, payload);
        case PONG -> listener.onPong(this, payload);
        default -> throw new WsCloseException("invalid-op-code", WsCloseCodes.PROTOCOL_ERROR);
        }
        return true;
    }

    private ServerWsFrame readFrame() {
        try {
            // TODO check may payload size, danger of oom
            return ServerWsFrame.read(helidonSocket, connection.reader(), Integer.MAX_VALUE);
        } catch (WsCloseException e) {
            close(e.closeCode(), e.getMessage());
            throw e;
        }
    }

    private enum ContinuationType {
        NONE,
        TEXT,
        BINARY
    }
}

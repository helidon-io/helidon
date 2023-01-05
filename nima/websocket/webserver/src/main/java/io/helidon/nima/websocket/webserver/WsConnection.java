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

package io.helidon.nima.websocket.webserver;

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.websocket.CloseCodes;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;

import static io.helidon.nima.websocket.webserver.WsUpgradeProvider.PROTOCOL;

class WsConnection implements ServerConnection, WsSession {
    private static final System.Logger LOGGER = System.getLogger(WsConnection.class.getName());

    private final ConnectionContext ctx;
    private final HttpPrologue prologue;
    private final WritableHeaders<?> headers;
    private final Headers upgradeHeaders;
    private final String wsKey;
    private final WsListener listener;

    private final BufferData sendBuffer = BufferData.growing(1024);
    private final DataReader dataReader;

    private ContinuationType recvContinuation = ContinuationType.NONE;
    private boolean sendContinuation;
    private boolean closeSent;

    WsConnection(ConnectionContext ctx,
                 HttpPrologue prologue,
                 WritableHeaders<?> headers,
                 Headers upgradeHeaders,
                 String wsKey,
                 WebSocket wsRoute) {
        this.ctx = ctx;
        this.prologue = prologue;
        this.headers = headers;
        this.upgradeHeaders = upgradeHeaders;
        this.wsKey = wsKey;
        this.listener = wsRoute.listener();
        this.dataReader = ctx.dataReader();
    }

    @Override
    public void handle() {
        listener.onOpen(this);
        while (true) {
            ClientFrame frame = readFrame();
            try {
                if (!processFrame(frame)) {
                    return;
                }
            } catch (Exception e) {
                listener.onError(this, e);
                this.close(CloseCodes.UNEXPECTED_CONDITION, e.getMessage());
                return;
            }
        }
    }

    @Override
    public WsSession send(String text, boolean last) {
        return send(ServerFrame.data(text, last));
    }

    @Override
    public WsSession send(BufferData bufferData, boolean last) {
        return send(ServerFrame.data(bufferData, last));
    }

    @Override
    public WsSession ping(BufferData bufferData) {
        return send(ServerFrame.control(WsOpCode.PING, bufferData));
    }

    @Override
    public WsSession pong(BufferData bufferData) {
        return send(ServerFrame.control(WsOpCode.PONG, bufferData));
    }

    @Override
    public WsSession close(int code, String reason) {
        closeSent = true;
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(2 + reasonBytes.length);
        bufferData.writeInt16(code);
        bufferData.write(reasonBytes);

        return send(ServerFrame.control(WsOpCode.CLOSE, bufferData));
    }

    @Override
    public WsSession abort() {
        close(CloseCodes.NORMAL_CLOSE, "Abort");
        throw new CloseConnectionException("Aborting from WebSocket");
    }

    @Override
    public Optional<String> subProtocol() {
        if (upgradeHeaders != null) {
            return upgradeHeaders.first(PROTOCOL);
        }
        return Optional.empty();
    }

    private boolean processFrame(ClientFrame frame) {
        // TODO listener.onError should be called for errors
        BufferData payload = frame.unmasked();
        switch (frame.opCode()) {
        case CONTINUATION -> {
            boolean finalFrame = frame.fin();
            ContinuationType ct = recvContinuation;
            if (finalFrame) {
                recvContinuation = ContinuationType.NONE;
            }
            switch (ct) {
            case TEXT -> listener.receive(this, payload.readString(payload.available(), StandardCharsets.UTF_8), finalFrame);
            case BINARY -> listener.receive(this, payload, finalFrame);
            default -> {
                close(CloseCodes.PROTOCOL_ERROR, "Unexpected continuation received");
                throw new CloseConnectionException("Websocket unexpected continuation");
            }
            }
        }
        case TEXT -> {
            recvContinuation = ContinuationType.TEXT;
            listener.receive(this, payload.readString(payload.available(), StandardCharsets.UTF_8), frame.fin());
        }
        case BINARY -> {
            recvContinuation = ContinuationType.BINARY;
            listener.receive(this, payload, frame.fin());
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
            if (!closeSent) {
                close(CloseCodes.NORMAL_CLOSE, "normal");
            }
            return false;
        }
        case PING -> listener.onPing(this, payload);
        case PONG -> listener.onPong(this, payload);
        default -> throw new IllegalStateException("Invalid frame opCode: " + frame.opCode());
        }
        return true;
    }

    private ClientFrame readFrame() {
        /*
         Frame header
         */
        // byte 0
        int opCodeByte = dataReader.read();
        boolean fin = (opCodeByte & 0b10000000) != 0;
        int extensionFlags = opCodeByte & 0b01110000;
        if (extensionFlags != 0) {
            close(CloseCodes.PROTOCOL_ERROR, "Extension flags defined where none should be");
            throw new CloseConnectionException("Websocket extension flags defined where none should be");
        }
        WsOpCode opCode = WsOpCode.get(opCodeByte & 0b00001111);

        // byte 1 (possible to byte 9 if maximal number of bytes used for length)
        int lenByte = dataReader.read();
        boolean masked = (lenByte & 0b10000000) != 0;

        if (!masked) {
            close(1002, "Unmasked client request");
            throw new CloseConnectionException("Websocket unmasked client request");
        }
        long frameLength;
        int length = lenByte & 0b01111111;
        if (length < 126) {
            frameLength = length;
        } else if (length == 126) {
            frameLength = dataReader.readBuffer(2).readInt16();
        } else {
            frameLength = dataReader.readBuffer(8).readLong();
        }
        // TODO check may payload size, danger of oom
        if (frameLength < 0) {
            close(CloseCodes.PROTOCOL_ERROR, "Negative payload length");
            throw new CloseConnectionException("Negative websocket payload length");
        }
        if (frameLength > Integer.MAX_VALUE) {
            close(CloseCodes.TOO_BIG, "Payload too large");
            throw new CloseConnectionException("Websocket payload too large");
        }

        // next 2 bytes - masking key
        int[] maskingKey = new int[4];
        maskingKey[0] = dataReader.read();
        maskingKey[1] = dataReader.read();
        maskingKey[2] = dataReader.read();
        maskingKey[3] = dataReader.read();

        // next frameLength bytes - actual payload
        // we can safely cast to int, as we make sure it is smaller or equal to MAX_INT
        BufferData payload = dataReader.readBuffer((int) frameLength);

        ClientFrame frame = new ClientFrame(opCode,
                                            frameLength,
                                            payload,
                                            fin,
                                            maskingKey);

        if (LOGGER.isLoggable(Level.TRACE)) {
            ctx.log(LOGGER, Level.TRACE, "ws frame recv %s", frame);
        }

        return frame;
    }

    private WsSession send(ServerFrame frame) {
        WsOpCode usedCode = frame.opCode();
        if (frame.isPayload()) {
            // check if continuation or set continuation
            if (sendContinuation) {
                usedCode = WsOpCode.CONTINUATION;
            }

            // do not change type for the first frame
            sendContinuation = !frame.fin();
        }

        frame.opCode(usedCode);

        if (LOGGER.isLoggable(Level.TRACE)) {
            ctx.log(LOGGER, Level.TRACE, "ws frame send %s", frame);
        }

        sendBuffer.clear();
        int opCodeFull = frame.fin() ? 0b10000000 : 0;
        opCodeFull |= usedCode.code();
        sendBuffer.write(opCodeFull);

        if (frame.payloadLength() < 126) {
            sendBuffer.write((int) frame.payloadLength());
            //TODO finish other options
        }
        sendBuffer.write(frame.payloadData());
        ctx.dataWriter().writeNow(sendBuffer);
        return this;
    }

    private enum ContinuationType {
        NONE,
        TEXT,
        BINARY
    }
}

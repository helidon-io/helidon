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
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.websocket.ClientWsFrame;
import io.helidon.nima.websocket.ServerWsFrame;
import io.helidon.nima.websocket.WsCloseCodes;
import io.helidon.nima.websocket.WsCloseException;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsOpCode;
import io.helidon.nima.websocket.WsSession;

import static io.helidon.nima.websocket.webserver.WsUpgrader.PROTOCOL;

/**
 * WebSocket connection, server side session implementation.
 */
public class WsConnection implements ServerConnection, WsSession {
    private static final System.Logger LOGGER = System.getLogger(WsConnection.class.getName());

    private final ConnectionContext ctx;
    private final HttpPrologue prologue;
    private final Headers upgradeHeaders;
    private final String wsKey;
    private final WsListener listener;

    private final BufferData sendBuffer = BufferData.growing(1024);
    private final DataReader dataReader;

    private ContinuationType recvContinuation = ContinuationType.NONE;
    private boolean sendContinuation;
    private boolean closeSent;

    private WsConnection(ConnectionContext ctx,
                         HttpPrologue prologue,
                         Headers upgradeHeaders,
                         String wsKey,
                         WsRoute wsRoute) {
        this.ctx = ctx;
        this.prologue = prologue;
        this.upgradeHeaders = upgradeHeaders;
        this.wsKey = wsKey;
        this.listener = wsRoute.listener();
        this.dataReader = ctx.dataReader();
    }

    /**
     * Create a new connection.
     *
     * @param ctx            server connection context
     * @param prologue       prologue of this request
     * @param upgradeHeaders headers for
     * @param wsKey          ws key
     * @param wsRoute        route to use
     * @return a new connection
     */
    public static WsConnection create(ConnectionContext ctx,
                                      HttpPrologue prologue,
                                      Headers upgradeHeaders,
                                      String wsKey,
                                      WsRoute wsRoute) {
        return new WsConnection(ctx, prologue, upgradeHeaders, wsKey, wsRoute);
    }

    @Override
    public void handle() {
        listener.onOpen(this);
        while (true) {
            ClientWsFrame frame = readFrame();
            try {
                if (!processFrame(frame)) {
                    return;
                }
            } catch (CloseConnectionException e) {
                throw e;
            } catch (Exception e) {
                listener.onError(this, e);
                this.close(WsCloseCodes.UNEXPECTED_CONDITION, e.getMessage());
                return;
            }
        }
    }

    @Override
    public WsSession send(String text, boolean last) {
        return send(ServerWsFrame.data(text, last));
    }

    @Override
    public WsSession send(BufferData bufferData, boolean last) {
        return send(ServerWsFrame.data(bufferData, last));
    }

    @Override
    public WsSession ping(BufferData bufferData) {
        return send(ServerWsFrame.control(WsOpCode.PING, bufferData));
    }

    @Override
    public WsSession pong(BufferData bufferData) {
        return send(ServerWsFrame.control(WsOpCode.PONG, bufferData));
    }

    @Override
    public WsSession close(int code, String reason) {
        closeSent = true;
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(2 + reasonBytes.length);
        bufferData.writeInt16(code);
        bufferData.write(reasonBytes);

        return send(ServerWsFrame.control(WsOpCode.CLOSE, bufferData));
    }

    @Override
    public WsSession terminate() {
        close(WsCloseCodes.NORMAL_CLOSE, "Terminate");
        throw new CloseConnectionException("Terminate from WebSocket");
    }

    @Override
    public Optional<String> subProtocol() {
        return upgradeHeaders.first(PROTOCOL);
    }

    private boolean processFrame(ClientWsFrame frame) {
        BufferData payload = frame.payloadData();
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
                close(WsCloseCodes.PROTOCOL_ERROR, "Unexpected continuation received");
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
                close(WsCloseCodes.NORMAL_CLOSE, "normal");
            }
            return false;
        }
        case PING -> listener.onPing(this, payload);
        case PONG -> listener.onPong(this, payload);
        default -> throw new IllegalStateException("Invalid frame opCode: " + frame.opCode());
        }
        return true;
    }

    private ClientWsFrame readFrame() {
        try {
            // TODO check may payload size, danger of oom
            return ClientWsFrame.read(ctx, dataReader, Integer.MAX_VALUE);
        } catch (WsCloseException e) {
            close(e.closeCode(), e.getMessage());
            throw new CloseConnectionException("WebSocket failed to read client frame", e);
        }
    }

    private WsSession send(ServerWsFrame frame) {
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
            ctx.log(LOGGER, Level.TRACE, "ws server frame send %s", frame);
        }

        sendBuffer.clear();
        int opCodeFull = frame.fin() ? 0b10000000 : 0;
        opCodeFull |= usedCode.code();
        sendBuffer.write(opCodeFull);

        if (frame.payloadLength() < 126) {
            sendBuffer.write((int) frame.payloadLength());
            // TODO finish other options (payload longer than 126 bytes)
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

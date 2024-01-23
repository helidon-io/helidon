/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.websocket;

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.http.DateTime;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.websocket.ClientWsFrame;
import io.helidon.websocket.ServerWsFrame;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsCloseException;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsOpCode;
import io.helidon.websocket.WsSession;

/**
 * WebSocket connection, server side session implementation.
 */
public class WsConnection implements ServerConnection, WsSession {
    private static final System.Logger LOGGER = System.getLogger(WsConnection.class.getName());

    static final int MAX_FRAME_LENGTH = 1048576;

    private final ConnectionContext ctx;
    private final HttpPrologue prologue;
    private final Headers upgradeHeaders;
    private final String wsKey;
    private final WsListener listener;
    private final WsConfig wsConfig;

    private final BufferData sendBuffer = BufferData.growing(1024);
    private final DataReader dataReader;

    private ContinuationType recvContinuation = ContinuationType.NONE;
    private boolean sendContinuation;
    private boolean closeSent;

    private volatile Thread myThread;
    private volatile boolean canRun = true;
    private volatile boolean readingNetwork;
    private volatile ZonedDateTime lastRequestTimestamp;

    private WsConnection(ConnectionContext ctx,
                         HttpPrologue prologue,
                         Headers upgradeHeaders,
                         String wsKey,
                         WsListener wsListener) {
        this.ctx = ctx;
        this.prologue = prologue;
        this.upgradeHeaders = upgradeHeaders;
        this.wsKey = wsKey;
        this.listener = wsListener;
        this.dataReader = ctx.dataReader();
        this.lastRequestTimestamp = DateTime.timestamp();
        this.wsConfig = (WsConfig) ctx.listenerContext()
                                      .config()
                                      .protocols()
                                      .stream()
                                      .filter(p -> p instanceof WsConfig)
                                      .findFirst()
                                      .orElseThrow(() -> new InternalError("Unable to find WebSocket config"));
    }

    /**
     * Create a new connection using a listener.
     *
     * @param ctx            server connection context
     * @param prologue       prologue of this request
     * @param upgradeHeaders headers for
     * @param wsKey          ws key
     * @param wsListener     a ws listener
     * @return a new connection
     */
    public static WsConnection create(ConnectionContext ctx,
                                      HttpPrologue prologue,
                                      Headers upgradeHeaders,
                                      String wsKey,
                                      WsListener wsListener) {
        return new WsConnection(ctx, prologue, upgradeHeaders, wsKey, wsListener);
    }

    /**
     * Create a new connection using a route.
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
        return new WsConnection(ctx, prologue, upgradeHeaders, wsKey, wsRoute.listener());
    }

    @Override
    public void handle(Semaphore requestSemaphore) {
        myThread = Thread.currentThread();
        listener.onOpen(this);

        if (requestSemaphore.tryAcquire()) {
            try {
                while (canRun) {
                    readingNetwork = true;
                    ClientWsFrame frame = readFrame();
                    readingNetwork = false;
                    lastRequestTimestamp = DateTime.timestamp();
                    try {
                        if (!processFrame(frame)) {
                            lastRequestTimestamp = DateTime.timestamp();
                            return;
                        }
                        lastRequestTimestamp = DateTime.timestamp();
                    } catch (CloseConnectionException e) {
                        throw e;
                    } catch (Exception e) {
                        listener.onError(this, e);
                        this.close(WsCloseCodes.UNEXPECTED_CONDITION, e.getMessage());
                        return;
                    }
                }
                this.close(WsCloseCodes.NORMAL_CLOSE, "Idle timeout");
            } finally {
                requestSemaphore.release();
            }
        } else {
            listener.onClose(this, WsCloseCodes.TRY_AGAIN_LATER, "Too Many Concurrent Requests");
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
        return upgradeHeaders.first(WsUpgrader.PROTOCOL);
    }

    @Override
    public Duration idleTime() {
        return Duration.between(lastRequestTimestamp, DateTime.timestamp());
    }

    @Override
    public void close(boolean interrupt) {
        // either way, finish
        this.canRun = false;

        if (interrupt) {
            // interrupt regardless of current state
            if (myThread != null) {
                myThread.interrupt();
            }
        } else if (readingNetwork) {
            // only interrupt when not processing a request (there is a chance of a race condition, this edge case
            // is ignored
            myThread.interrupt();
        }
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
            case TEXT -> listener.onMessage(this, payload.readString(payload.available(), StandardCharsets.UTF_8), finalFrame);
            case BINARY -> listener.onMessage(this, payload, finalFrame);
            default -> {
                close(WsCloseCodes.PROTOCOL_ERROR, "Unexpected continuation received");
                throw new CloseConnectionException("Websocket unexpected continuation");
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
            return ClientWsFrame.read(ctx, dataReader, wsConfig.maxFrameLength());
        } catch (DataReader.InsufficientDataAvailableException e) {
            throw new CloseConnectionException("Socket closed by the other side", e);
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

        long length = frame.payloadLength();
        if (length < 126) {
            sendBuffer.write((int) length);
        } else if (length < 1 << 16) {
            sendBuffer.write(126);
            sendBuffer.write((int) (length >>> 8));
            sendBuffer.write((int) (length & 0xFF));
        } else {
            sendBuffer.write(127);
            for (int i = 56; i >= 0; i -= 8){
                sendBuffer.write((int) (length >>> i) & 0xFF);
            }
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

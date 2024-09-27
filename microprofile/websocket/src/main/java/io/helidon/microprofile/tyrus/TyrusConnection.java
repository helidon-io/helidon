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

package io.helidon.microprofile.tyrus;

import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.concurrency.limits.BasicLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitException;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.DateTime;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import jakarta.websocket.CloseReason;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static jakarta.websocket.CloseReason.CloseCodes.getCloseCode;

/**
 * A server connection that passes and receives buffers from Tyrus. Note that this
 * connection does not handle framing, it simply passes raw bytes to Tyrus where
 * that takes place.
 */
class TyrusConnection implements ServerConnection, WsSession {
    private static final System.Logger LOGGER = System.getLogger(TyrusConnection.class.getName());

    private final ConnectionContext ctx;
    private final WebSocketEngine.UpgradeInfo upgradeInfo;
    private final TyrusListener listener;

    private volatile Thread myThread;
    private volatile boolean canRun = true;
    private volatile boolean readingNetwork;
    private volatile ZonedDateTime lastRequestTimestamp;

    TyrusConnection(ConnectionContext ctx, WebSocketEngine.UpgradeInfo upgradeInfo) {
        this.ctx = ctx;
        this.upgradeInfo = upgradeInfo;
        this.listener = new TyrusListener();
        this.lastRequestTimestamp = DateTime.timestamp();
    }

    @Override
    public void handle(Limit limit) {
        myThread = Thread.currentThread();
        DataReader dataReader = ctx.dataReader();

        try {
            limit.invoke(() -> listener.onOpen(this));
        } catch (LimitException e) {
            listener.onError(this, e);
            throw new CloseConnectionException("Too many concurrent requests");
        } catch (Exception e) {
            listener.onError(this, e);
            listener.onClose(this, WsCloseCodes.UNEXPECTED_CONDITION, e.getMessage());
            return;
        }

        while (canRun) {
            try {
                readingNetwork = true;
                BufferData buffer = dataReader.readBuffer();
                readingNetwork = false;
                lastRequestTimestamp = DateTime.timestamp();
                limit.invoke(() -> listener.onMessage(this, buffer, true));
                lastRequestTimestamp = DateTime.timestamp();
            } catch (LimitException e) {
                listener.onClose(this, WsCloseCodes.TRY_AGAIN_LATER, "Too Many Concurrent Requests");
                return;
            } catch (Exception e) {
                listener.onError(this, e);
                listener.onClose(this, WsCloseCodes.UNEXPECTED_CONDITION, e.getMessage());
                return;
            }
        }
        listener.onClose(this, WsCloseCodes.NORMAL_CLOSE, "Idle timeout");
    }

    @SuppressWarnings("removal")
    @Override
    public void handle(Semaphore requestSemaphore) {
        handle(BasicLimit.create(requestSemaphore));
    }

    @Override
    public WsSession send(String text, boolean last) {
        return this;
    }

    @Override
    public WsSession send(BufferData bufferData, boolean last) {
        return this;
    }

    @Override
    public WsSession ping(BufferData bufferData) {
        return this;
    }

    @Override
    public WsSession pong(BufferData bufferData) {
        return this;
    }

    @Override
    public WsSession close(int code, String reason) {
        return this;
    }

    @Override
    public WsSession terminate() {
        return this;
    }

    @Override
    public Optional<String> subProtocol() {
        return Optional.empty();
    }

    @Override
    public SocketContext socketContext() {
        return ctx;
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

    class TyrusListener implements WsListener {
        private static final int MAX_RETRIES = 5;

        private Connection connection;

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            // Should never be called!
        }

        @Override
        public void onMessage(WsSession session, BufferData buffer, boolean last) {
            byte[] b = new byte[buffer.available()];
            buffer.read(b);         // buffer copy!
            writeToTyrus(session, ByteBuffer.wrap(b));
        }

        @Override
        public void onClose(WsSession session, int status, String reason) {
            connection.close(new CloseReason(getCloseCode(status), reason));
        }

        @Override
        public void onError(WsSession session, Throwable t) {
            connection.close(new CloseReason(UNEXPECTED_CONDITION, t.getMessage()));
        }

        @Override
        public void onOpen(WsSession session) {
            Writer writer = new Writer() {
                @Override
                public void close() {
                }

                @Override
                public void write(ByteBuffer byteBuffer, CompletionHandler<ByteBuffer> completionHandler) {
                    byte[] b = new byte[byteBuffer.remaining()];
                    byteBuffer.get(b);      // buffer copy!
                    ctx.dataWriter().writeNow(BufferData.create(b));    // direct write to ctx
                    completionHandler.completed(byteBuffer);
                }
            };
            connection = upgradeInfo.createConnection(writer, TyrusListener::close);
        }

        /**
         * Writes a buffer to Tyrus. May retry a few times given that Tyrus may
         * not be able to read all bytes at once.
         *
         * @param session the session
         * @param nioBuffer the buffer to write
         */
        private void writeToTyrus(WsSession session, ByteBuffer nioBuffer) {
            int retries = MAX_RETRIES;
            while (nioBuffer.remaining() > 0 && retries-- > 0) {
                connection.getReadHandler().handle(nioBuffer);
            }

            // If we can't push all data to Tyrus, cancel and report problem
            if (retries == 0) {
                String reason = "Tyrus did not consume all data after " + MAX_RETRIES + " retries";
                connection.close(new CloseReason(UNEXPECTED_CONDITION, reason));
            }
        }

        private static void close(CloseReason closeReason) {
            LOGGER.log(Level.DEBUG, () -> "Connection closed: " + closeReason);
        }
    }
}

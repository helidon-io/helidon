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

package io.helidon.microprofile.tyrus;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.websocket.CloseCodes;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;
import io.helidon.nima.websocket.webserver.WsConnection;

import jakarta.websocket.CloseReason;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

import static jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static jakarta.websocket.CloseReason.CloseCodes.getCloseCode;

class TyrusConnection extends WsConnection {
    private static final Logger LOGGER = Logger.getLogger(TyrusConnection.class.getName());

    TyrusConnection(ConnectionContext ctx,
                    HttpPrologue prologue,
                    WritableHeaders<?> headers,
                    String wsKey,
                    WebSocketEngine.UpgradeInfo upgradeInfo) {
        super(ctx, prologue, headers, wsKey, new TyrusListener(upgradeInfo, ctx));
    }

    @Override
    public void handle() {
        DataReader dataReader = connectionContext().dataReader();
        TyrusListener listener = (TyrusListener) listener();

        listener.onOpen(this);
        while (true) {
            try {
                BufferData buffer = dataReader.readBuffer();
                listener.receive(this, buffer, true);
            } catch (Exception e) {
                listener.onError(this, e);
                this.close(CloseCodes.UNEXPECTED_CONDITION, e.getMessage());
                return;
            }
        }
    }

    static class TyrusListener implements WsListener {
        private static final int MAX_RETRIES = 5;

        private Connection connection;
        private final ConnectionContext ctx;
        private final WebSocketEngine.UpgradeInfo upgradeInfo;

        TyrusListener(WebSocketEngine.UpgradeInfo upgradeInfo, ConnectionContext ctx) {
            this.upgradeInfo = upgradeInfo;
            this.ctx = ctx;
        }

        @Override
        public void receive(WsSession session, String text, boolean last) {
            // Should never be called!
        }

        @Override
        public void receive(WsSession session, BufferData buffer, boolean last) {
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
                    session.close(NORMAL_CLOSURE.getCode(), "");
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

        private void writeToTyrus(WsSession session, ByteBuffer nioBuffer) {
            // Pass all data to Tyrus spi
            int retries = MAX_RETRIES;
            while (nioBuffer.remaining() > 0 && retries-- > 0) {
                connection.getReadHandler().handle(nioBuffer);
            }

            // If we can't push all data to Tyrus, cancel and report problem
            if (retries == 0) {
                String reason = "Tyrus did not consume all data after " + MAX_RETRIES + " retries";
                session.close(UNEXPECTED_CONDITION.getCode(), reason);
                connection.close(new CloseReason(UNEXPECTED_CONDITION, reason));
            }
        }

        private static void close(CloseReason closeReason) {
            LOGGER.log(Level.FINE, () -> "Connection closed: " + closeReason);
        }
    }
}

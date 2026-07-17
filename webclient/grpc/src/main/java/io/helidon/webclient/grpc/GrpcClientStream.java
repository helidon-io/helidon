/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.socket.SocketContext;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.http2.Http2ClientConfig;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2ClientStream;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.LockingStreamIdSequence;

class GrpcClientStream extends Http2ClientStream {
    private final ResetNotifier resetNotifier = new ResetNotifier();

    GrpcClientStream(Http2ClientConnection connection,
                     Http2Settings serverSettings,
                     SocketContext ctx,
                     Http2StreamConfig http2StreamConfig,
                     Http2ClientConfig http2ClientConfig,
                     LockingStreamIdSequence streamIdSeq,
                     Http2ClientImpl http2Client) {
        super(connection, serverSettings, ctx, http2StreamConfig, http2ClientConfig, streamIdSeq, http2Client);
    }

    @Override
    public boolean rstStream(Http2RstStream rstStream) {
        boolean handled;
        try {
            resetNotifier.reset(rstStream);
        } finally {
            handled = super.rstStream(rstStream);
        }
        return handled;
    }

    void onReset(Consumer<Http2RstStream> resetHandler) {
        resetNotifier.handler(resetHandler);
    }

    static final class ResetNotifier {
        private final AtomicReference<Consumer<Http2RstStream>> handler = new AtomicReference<>();
        private final AtomicReference<Http2RstStream> reset = new AtomicReference<>();
        private final AtomicBoolean delivered = new AtomicBoolean();

        void handler(Consumer<Http2RstStream> handler) {
            this.handler.set(Objects.requireNonNull(handler));
            deliver();
        }

        void reset(Http2RstStream reset) {
            this.reset.compareAndSet(null, Objects.requireNonNull(reset));
            deliver();
        }

        private void deliver() {
            Consumer<Http2RstStream> handler = this.handler.get();
            Http2RstStream reset = this.reset.get();
            if (handler != null && reset != null && delivered.compareAndSet(false, true)) {
                handler.accept(reset);
            }
        }
    }
}

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

package io.helidon.http.http2;

import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;

final class Http2FrameListenerUtil {
    private Http2FrameListenerUtil() {
    }

    static Http2FrameListener toSingleListener(List<Http2FrameListener> sendFrameListeners) {
        if (sendFrameListeners.isEmpty()) {
            return NoOpFrameListener.INSTANCE;
        } else if (sendFrameListeners.size() == 1) {
            return sendFrameListeners.get(0);
        } else {
            return new ListFrameListener(sendFrameListeners);
        }
    }

    private static final class NoOpFrameListener implements Http2FrameListener {
        private static final NoOpFrameListener INSTANCE = new NoOpFrameListener();
    }

    private static final class ListFrameListener implements Http2FrameListener {
        private final List<Http2FrameListener> delegates;

        ListFrameListener(List<Http2FrameListener> delegates) {
            this.delegates = delegates;
        }

        @Override
        public void frameHeader(SocketContext ctx, int streamId, BufferData frameHeader) {
            delegates.forEach(it -> it.frameHeader(ctx, streamId, frameHeader));
        }

        @Override
        public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
            delegates.forEach(it -> it.frameHeader(ctx, streamId, header));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, BufferData data) {
            delegates.forEach(it -> it.frame(ctx, streamId, data));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2Priority priority) {
            delegates.forEach(it -> it.frame(ctx, streamId, priority));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2RstStream rstStream) {
            delegates.forEach(it -> it.frame(ctx, streamId, rstStream));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2Settings settings) {
            delegates.forEach(it -> it.frame(ctx, streamId, settings));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2Ping ping) {
            delegates.forEach(it -> it.frame(ctx, streamId, ping));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2GoAway goAway) {
            delegates.forEach(it -> it.frame(ctx, streamId, goAway));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2WindowUpdate windowUpdate) {
            delegates.forEach(it -> it.frame(ctx, streamId, windowUpdate));
        }

        @Override
        public void headers(SocketContext ctx, int streamId, Http2Headers headers) {
            delegates.forEach(it -> it.headers(ctx, streamId, headers));
        }

        @Override
        public void frame(SocketContext ctx, int streamId, Http2Continuation continuation) {
            delegates.forEach(it -> it.frame(ctx, streamId, continuation));
        }
    }
}

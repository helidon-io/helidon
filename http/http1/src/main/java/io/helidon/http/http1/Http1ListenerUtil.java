/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.http1;

import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;

final class Http1ListenerUtil {
    private Http1ListenerUtil() {
    }

    static Http1ConnectionListener toSingleListener(List<Http1ConnectionListener> listeners) {
        if (listeners.isEmpty()) {
            return NoOpListener.INSTANCE;
        }
        if (listeners.size() == 1) {
            return listeners.getFirst();
        }
        return new ListListener(List.copyOf(listeners));
    }

    private static final class NoOpListener implements Http1ConnectionListener {
        private static final NoOpListener INSTANCE = new NoOpListener();

        private NoOpListener() {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    }

    private static final class ListListener implements Http1ConnectionListener {
        private final List<Http1ConnectionListener> listeners;

        private ListListener(List<Http1ConnectionListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public void prologue(SocketContext ctx, HttpPrologue prologue) {
            listeners.forEach(it -> it.prologue(ctx, prologue));
        }

        @Override
        public void headers(SocketContext ctx, Headers headers) {
            listeners.forEach(it -> it.headers(ctx, headers));
        }

        @Override
        public void status(SocketContext ctx, Status status) {
            listeners.forEach(it -> it.status(ctx, status));
        }

        @Override
        public void data(SocketContext ctx, BufferData data) {
            listeners.forEach(it -> it.data(ctx, data));
        }

        @Override
        public void data(SocketContext ctx, byte[] data, int position, int length) {
            listeners.forEach(it -> it.data(ctx, data, position, length));
        }

        @Override
        public boolean enabled() {
            return listeners.stream()
                    .anyMatch(Http1ConnectionListener::enabled);
        }
    }
}

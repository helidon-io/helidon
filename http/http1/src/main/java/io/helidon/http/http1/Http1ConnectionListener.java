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

import io.helidon.common.Api;
import io.helidon.common.buffers.DataListener;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;

/**
 * A listener for connection events.
 */
@Api.Internal
public interface Http1ConnectionListener extends DataListener<SocketContext> {
    /**
     * Create a new composite listener.
     *
     * @param listeners listeners to use
     * @return a new composite listener
     */
    static Http1ConnectionListener create(List<Http1ConnectionListener> listeners) {
        return Http1ListenerUtil.toSingleListener(listeners);
    }

    /**
     * Whether this listener is enabled. If set to {@code false} construction of intermediate objects may be skipped
     * as well as a method invocation on this listener.
     *
     * @return whether this listener is enabled
     */
    default boolean enabled() {
        return true;
    }

    /**
     * Handle prologue.
     *
     * @param ctx      context
     * @param prologue prologue
     */
    default void prologue(SocketContext ctx, HttpPrologue prologue) {
    }

    /**
     * Handle headers.
     *
     * @param ctx     context
     * @param headers headers
     */
    default void headers(SocketContext ctx, Headers headers) {
    }

    /**
     * Handle status (server response only).
     *
     * @param ctx    context
     * @param status status
     */
    default void status(SocketContext ctx, Status status) {
    }
}

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

package io.helidon.webserver.http1;

import java.util.List;

import io.helidon.common.buffers.DataListener;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;

/**
 * Connection listener for HTTP/1.1.
 */
public interface Http1ConnectionListener extends DataListener<ConnectionContext> {
    /**
     * Create a new listener from a list of listeners.
     *
     * @param listeners listeners to use
     * @return a single listener
     */
    static Http1ConnectionListener create(List<Http1ConnectionListener> listeners) {
        return Http1ConnectionListenerUtil.toSingleListener(listeners);
    }

    /**
     * Handle prologue.
     *
     * @param ctx      context
     * @param prologue prologue
     */
    default void prologue(ConnectionContext ctx, HttpPrologue prologue) {
    }

    /**
     * Handle headers.
     *
     * @param ctx     context
     * @param headers headers
     */
    default void headers(ConnectionContext ctx, Headers headers) {
    }

    /**
     * Handle status (server response only).
     *
     * @param ctx    context
     * @param status status
     */
    default void status(ConnectionContext ctx, Status status) {
    }
}

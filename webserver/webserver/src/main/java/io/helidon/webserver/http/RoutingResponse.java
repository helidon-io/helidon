/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import io.helidon.http.HttpPrologue;

/**
 * Routing response of a server.
 */
public interface RoutingResponse extends ServerResponse {
    /**
     * Reset routing information (nexted, rerouted etc.).
     */
    void resetRouting();

    /**
     * Should we reroute this exchange.
     *
     * @return whether rerouting was requested
     */
    boolean shouldReroute();

    /**
     * A new, rerouted prologue.
     *
     * @param prologue current prologue
     * @return prologue to use when rerouting
     */
    HttpPrologue reroutePrologue(HttpPrologue prologue);

    /**
     * Whether this request is nexted ({@link #next()} was called).
     *
     * @return if nexted
     */
    boolean isNexted();

    /**
     * Whether this request has an entity.
     *
     * @return whether has entity
     */
    boolean hasEntity();

    /**
     * Return true if the underlying response buffers and headers can be reset and a new response can be sent.
     *
     * @return {@code true} if reset was successful and a new response can be created instead of the existing one,
     *         {@code false} if reset failed and status and headers (and maybe entity bytes) were already sent
     */
    boolean reset();

    /**
     * Commit the response. This is mostly useful for output stream based responses, where we may want to delay
     * closing the output stream to handle errors, when route uses try with resources.
     * After this method is called, response cannot be {@link #reset()}.
     */
    void commit();

    /**
     * Return true if the underlying response buffers can be reset and a new response can be sent.
     * <p>
     * As opposed to {@link #reset()}, this method is not expected to reset headers already configured on the response
     * <p>
     * This method calls {@link #reset()} by default.
     *
     * @return {@code true} if reset was successful and a new response can be created instead of the existing one,
     *         {@code false} if reset failed and status and headers (and maybe entity bytes) were already sent
     */
    default boolean resetStream() {
        return reset();
    }
}

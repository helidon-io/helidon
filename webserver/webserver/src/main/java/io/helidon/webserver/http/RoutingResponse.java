/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.io.OutputStream;
import java.util.Objects;
import java.util.function.UnaryOperator;

import io.helidon.common.Api;
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

    /**
     * Reset the response entity so an unsent response can be replaced while preserving response metadata unrelated to
     * the entity, such as CORS, cookies, cache controls, and {@code Vary} headers.
     * <p>
     * This method is intended for Helidon infrastructure that replaces a failed response entity.
     * <p>
     * This resets entity buffers and removes framing, representation, validator, range, and trailer headers.
     * Implementations with separate trailer state must reset that state as well.
     * @return {@code true} if reset was successful and a new entity can be created instead of the existing one,
     *         {@code false} if reset failed and status and headers (and maybe entity bytes) were already sent
     */
    @Api.Internal
    boolean resetEntity();

    /**
     * Configure an infrastructure output stream filter that remains registered when an unsent response entity is reset.
     * <p>
     * This method is intended for Helidon features that must observe or transform both the original response entity and any
     * replacement entity produced by error handling. Application filters should use {@link #streamFilter(UnaryOperator)}.
     *
     * @param filterFunction function that wraps the response output stream
     */
    @Api.Internal
    default void persistentStreamFilter(UnaryOperator<OutputStream> filterFunction) {
        streamFilter(Objects.requireNonNull(filterFunction));
    }

    /**
     * Register an infrastructure listener that remains registered when an unsent response entity is reset.
     * <p>
     * This method is intended for Helidon features that must prepare both the original response entity and any replacement
     * entity produced by error handling. Application listeners should use {@link #beforeSend(Runnable)}.
     *
     * @param listener listener invoked before the response is sent
     */
    @Api.Internal
    default void persistentBeforeSend(Runnable listener) {
        beforeSend(Objects.requireNonNull(listener));
    }
}

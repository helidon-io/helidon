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

package io.helidon.webserver.spi;

import java.time.Duration;

/**
 * Transport binding owned by a logical WebServer listener.
 * <p>
 * A binding is responsible for one transport endpoint of a listener, such as a TCP server socket, a Unix domain socket,
 * or another provider-defined transport. The listener owns shared request routing and shared lifecycle state; each binding
 * owns transport-specific resources and active transport work.
 */
public interface TransportBinding {
    /**
     * Transport binding type.
     * <p>
     * This is the provider key used by {@link TransportBindingFactoryProvider} and by configuration under
     * {@code server.bindings}.
     *
     * @return transport binding type
     */
    String type();

    /**
     * Transport binding name.
     * <p>
     * This distinguishes bindings within a listener for uniqueness checks and diagnostics.
     *
     * @return transport binding name
     */
    default String name() {
        return type();
    }

    /**
     * Configured endpoint summary for diagnostics.
     *
     * @return configured endpoint summary
     */
    String configuredEndpoint();

    /**
     * Transport security applied by this binding.
     * <p>
     * Bindings that return {@link Security#TLS} must use listener TLS configuration, including listener virtual host TLS
     * configuration when present. Bindings that are protected without listener TLS should return
     * {@link Security#TLS_EQUIVALENT}. Bindings that do not provide transport security should return
     * {@link Security#UNPROTECTED}.
     *
     * @return transport security
     */
    Security security();

    /**
     * Start the binding.
     * <p>
     * If startup fails after opening transport resources, the binding must still allow {@link #stop(Duration)} to clean up
     * any partially started state.
     */
    void start();

    /**
     * Stop accepting new work and shut down active transport work.
     * <p>
     * Implementations must stop accepting new work and release transport resources before returning. Active work must
     * either drain or be force-stopped within the graceful period.
     *
     * @param gracefulPeriod maximum graceful shutdown period
     * @return shutdown result
     */
    ShutdownResult stop(Duration gracefulPeriod);

    /**
     * Suspend the binding for checkpoint.
     */
    default void suspend() {
    }

    /**
     * Resume the binding after restore.
     */
    default void resume() {
    }

    /**
     * Transport binding shutdown result.
     */
    enum ShutdownResult {
        /**
         * Binding stopped gracefully without force-stopping active work.
         */
        GRACEFUL,

        /**
         * Binding had to force-stop active work after the graceful shutdown period elapsed.
         */
        FORCED
    }

    /**
     * Transport binding security.
     */
    enum Security {
        /**
         * Binding protects the endpoint with listener TLS configuration.
         */
        TLS,

        /**
         * Binding protects the endpoint by provider-defined means that are equivalent to TLS from the listener security
         * policy perspective, but does not use listener TLS configuration.
         */
        TLS_EQUIVALENT,

        /**
         * Binding does not protect the endpoint.
         */
        UNPROTECTED
    }
}

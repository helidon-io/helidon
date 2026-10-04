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

package io.helidon.webclient.api;

import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;

/**
 * Internal WebClient integration for HTTP transport observers.
 */
@Api.Internal
public final class HttpTransportObserverSupport {
    private HttpTransportObserverSupport() {
    }

    /**
     * Configures observation before connecting a physical transport.
     *
     * @param connection unconnected transport
     * @param observer observer owned by the connection cache
     * @param <T> connection type
     * @return the same connection
     */
    public static <T extends ClientConnection> T observe(T connection, HttpTransportObserver observer) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(observer, "observer");
        if (observer != HttpTransportObserver.noop() && connection instanceof ConnectionObservationContext context) {
            context.httpTransportObserver(observer);
        }
        return connection;
    }

    /**
     * Returns a connection's serialized observation facade.
     *
     * @param connection physical transport
     * @return connection observation, or the canonical no-op for an unobserved transport
     */
    public static ConnectionObservation connection(ClientConnection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection instanceof ConnectionObservationContext context) {
            return context.httpTransportObservation();
        }
        return ConnectionObservation.noop();
    }

    /**
     * Records the terminal outcome selected by a protocol.
     *
     * @param connection physical transport
     * @param outcome terminal outcome
     */
    public static void connectionOutcome(ClientConnection connection, ConnectionOutcome outcome) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(outcome, "outcome");
        if (connection instanceof ConnectionObservationContext context) {
            context.httpTransportOutcome(outcome);
        }
    }

    /**
     * Records an actual transport failure before its connection is closed.
     *
     * @param connection physical transport
     * @param failure transport failure
     */
    public static void connectionFailed(ClientConnection connection, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        connectionOutcome(connection, isTimeout(failure) ? ConnectionOutcome.TIMEOUT : ConnectionOutcome.ERROR);
    }

    static boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    /**
     * Transport-only capability of a configured WebClient service.
     * Implementations are omitted from the per-request service chain.
     */
    public interface ObserverProvider {
        /**
         * Whether observation is configured, without resolving a registry or acquiring resources.
         *
         * @return whether observation is enabled
         */
        boolean enabled();

        /**
         * Stable sharing identity for compatible observations, compared by identity.
         * Called only for enabled providers when their first connection cache is acquired.
         *
         * @return non-null scope identity
         */
        Object scope();

        /**
         * Creates a fresh lifecycle owned by one cache partition.
         *
         * @return observer lifecycle
         */
        ObserverLifecycle createObserver();
    }

    /**
     * Observer lease lifecycle owned by a client connection cache partition.
     */
    public interface ObserverLifecycle {
        /**
         * Starts observation for the partition.
         *
         * @return observer for physical connections created by the partition
         */
        HttpTransportObserver start();

        /**
         * Releases observation after the cache has retired its connections.
         * Outstanding physical connections may finish asynchronously. A registry owner must await
         * final observer completion before closing its registry.
         *
         * @return completion of observer release
         */
        CompletionStage<Void> stop();
    }

    /**
     * Internal physical connection observation context.
     */
    public interface ConnectionObservationContext {
        /**
         * Configures the observer before connecting.
         *
         * @param observer transport observer
         */
        void httpTransportObserver(HttpTransportObserver observer);

        /**
         * Returns the connection's serialized observation facade.
         *
         * @return observation or canonical no-op
         */
        ConnectionObservation httpTransportObservation();

        /**
         * Records a terminal outcome before physical closure.
         *
         * @param outcome terminal outcome
         */
        void httpTransportOutcome(ConnectionOutcome outcome);
    }
}

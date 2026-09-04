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

package io.helidon.webserver;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Internal WebServer integration for HTTP transport observers.
 */
@Api.Internal
public final class HttpTransportObserverSupport {
    private HttpTransportObserverSupport() {
    }

    /**
     * Adds an observer to a configured listener.
     *
     * @param featureContext server feature context
     * @param socketName listener socket name
     * @param observerLifecycle observer lifecycle
     * @return whether the feature context supports transport observation
     */
    public static boolean addObserver(ServerFeature.ServerFeatureContext featureContext,
                                      String socketName,
                                      ObserverLifecycle observerLifecycle) {
        Objects.requireNonNull(featureContext, "featureContext");
        Objects.requireNonNull(socketName, "socketName");
        Objects.requireNonNull(observerLifecycle, "observerLifecycle");
        if (featureContext instanceof ServerFeatureContextImpl context) {
            context.addHttpTransportObserver(socketName, observerLifecycle);
            return true;
        }
        return false;
    }

    /**
     * Returns the active observer for a listener.
     *
     * @param listenerContext listener context
     * @return active observer, or a no-op observer for an unsupported context
     */
    public static HttpTransportObserver observer(ListenerContext listenerContext) {
        Objects.requireNonNull(listenerContext, "listenerContext");
        if (listenerContext instanceof ServerListener listener) {
            return listener.httpTransportObserver();
        }
        return HttpTransportObserver.noop();
    }

    /**
     * Returns the transport observation for a stream-based server connection.
     *
     * @param connectionContext connection context
     * @return connection observation, or a no-op observation for an unsupported context
     */
    public static ConnectionObservation connection(ConnectionContext connectionContext) {
        Objects.requireNonNull(connectionContext, "connectionContext");
        if (connectionContext instanceof ConnectionObservationContext observedContext) {
            return Objects.requireNonNull(observedContext.httpTransportObservation(), "connection observation");
        }
        return ConnectionObservation.noop();
    }

    /**
     * Records the terminal outcome selected by a protocol implementation.
     *
     * @param connectionContext connection context
     * @param outcome connection outcome
     */
    public static void connectionOutcome(ConnectionContext connectionContext,
                                         ConnectionOutcome outcome) {
        Objects.requireNonNull(connectionContext, "connectionContext");
        Objects.requireNonNull(outcome, "outcome");
        if (connectionContext instanceof ConnectionObservationContext observedContext) {
            observedContext.httpTransportOutcome(outcome);
        }
    }

    /**
     * Listener-scoped observer lifecycle owned by a server feature.
     *
     * <p>One lifecycle can be added to multiple listeners. Each successful {@link #start()} invocation is paired with one
     * {@link #stop()} invocation after that listener's transports have stopped. Lifecycle methods may be invoked concurrently
     * for different listeners, so implementations must be thread safe. Listener shutdown awaits the stage returned from
     * {@code stop} within the listener shutdown deadline.
     */
    public interface ObserverLifecycle {
        /**
         * Starts observation for one listener.
         *
         * @return observer for the listener
         */
        HttpTransportObserver start();

        /**
         * Stops observation for one listener.
         *
         * @return completion of observer cleanup for this listener
         */
        CompletionStage<Void> stop();
    }

    /**
     * Internal connection context which carries its physical transport observation.
     */
    public interface ConnectionObservationContext {
        /**
         * Returns the physical connection observation.
         *
         * @return connection observation
         */
        ConnectionObservation httpTransportObservation();

        /**
         * Records a terminal connection outcome if none has been recorded yet.
         *
         * @param outcome connection outcome
         */
        void httpTransportOutcome(ConnectionOutcome outcome);
    }
}

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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.webclient.spi.WebClientService;

/**
 * Internal access to WebClient transport observation.
 */
@Api.Internal
public final class WebClientTransportObserverSupport {
    private WebClientTransportObserverSupport() {
    }

    /**
     * Returns the observer owned or borrowed by a WebClient.
     *
     * @param webClient web client
     * @return transport observer
     */
    public static HttpTransportObserver observer(WebClient webClient) {
        Objects.requireNonNull(webClient, "webClient");
        if (webClient instanceof WebClientTransportObserverContext context) {
            return context.transportObserver();
        }
        return HttpTransportObserver.noop();
    }

    /**
     * Returns the identity used to partition shared connection caches.
     *
     * @param webClient web client
     * @return transport observer identity
     */
    public static Object observerIdentity(WebClient webClient) {
        Objects.requireNonNull(webClient, "webClient");
        if (webClient instanceof WebClientTransportObserverContext context) {
            return context.transportObserverIdentity();
        }
        return HttpTransportObserver.noop();
    }

    /**
     * Creates a service which borrows the observer and cache identity of a parent WebClient.
     *
     * <p>The borrowing service does not own or close the parent's observer registration. Its presence in a child
     * WebClient makes the borrowed observation domain exclusive: observer-provider facets of the child's other services
     * do not acquire registrations, while those services retain their normal request-chain behavior.
     *
     * @param webClient parent web client
     * @return borrowing service
     */
    public static WebClientService borrowingService(WebClient webClient) {
        return new BorrowingService(observer(webClient), observerIdentity(webClient));
    }

    /**
     * Returns the observation associated with a built-in client connection.
     *
     * @param connection client connection
     * @return connection observation
     */
    public static ConnectionObservation observation(ClientConnection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection instanceof ObservedClientConnection observed) {
            return observed.transportObservation();
        }
        return ConnectionObservation.noop();
    }

    /**
     * Publishes protocol selection for a built-in client connection.
     *
     * @param connection client connection
     * @param protocol selected protocol
     */
    public static void protocolSelected(ClientConnection connection, String protocol) {
        observation(connection).protocolSelected(Objects.requireNonNull(protocol, "protocol"));
    }

    /**
     * Closes a client connection with an explicit transport outcome.
     *
     * @param connection client connection
     * @param outcome connection outcome
     */
    public static void close(ClientConnection connection, ConnectionOutcome outcome) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(outcome, "outcome");
        if (connection instanceof ObservedClientConnection observed) {
            observed.closeResource(outcome);
        } else {
            connection.closeResource();
        }
    }

    private static final class BorrowingService implements WebClientService,
                                                           BorrowedWebClientTransportObserverProvider {
        private final HttpTransportObserver observer;
        private final Object identity;

        private BorrowingService(HttpTransportObserver observer, Object identity) {
            this.observer = observer;
            this.identity = identity;
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            return chain.proceed(request);
        }

        @Override
        public String type() {
            return "borrowed-http-transport-observer";
        }

        @Override
        public Object transportObserverIdentity() {
            return identity;
        }

        @Override
        public Registration openTransportObserver() {
            return new Registration() {
                @Override
                public HttpTransportObserver observer() {
                    return observer;
                }

                @Override
                public void close() {
                }

                @Override
                public CompletionStage<Void> completion() {
                    return CompletableFuture.completedFuture(null);
                }
            };
        }
    }
}

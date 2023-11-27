/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.inject.service.Injection;

/**
 * CircuitBreaker protects a potentially failing endpoint from overloading and the application
 * from spending resources on those endpoints.
 * <p>
 * In case too many errors are detected, the circuit opens and all new requests fail with a
 * {@link CircuitBreakerOpenException} for a period of time.
 * After this period, attempts are made to check if the service is up again - if so, the circuit closes
 * and requests can process as usual again.
 */
@RuntimeType.PrototypedBy(CircuitBreakerConfig.class)
@Injection.Contract
public interface CircuitBreaker extends FtHandler, RuntimeType.Api<CircuitBreakerConfig> {
    /**
     * Create a new circuit builder based on its configuration.
     *
     * @param config circuit breaker configuration
     * @return a new circuit breaker
     */
    static CircuitBreaker create(CircuitBreakerConfig config) {
        return new CircuitBreakerImpl(config);
    }

    /**
     * Create a new circuit breaker with a possibility to customize its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new circuit breaker
     */
    static CircuitBreaker create(Consumer<CircuitBreakerConfig.Builder> builderConsumer) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();
        builderConsumer.accept(builder);
        return create(builder.buildPrototype());
    }

    /**
     * A new circuit breaker fluent API builder.
     *
     * @return a new builder
     */
    static CircuitBreakerConfig.Builder builder() {
        return CircuitBreakerConfig.builder();
    }

    /**
     * Current breaker state.
     * As the state may change within nanoseconds, this is for information only.
     *
     * @return current breaker state
     */
    State state();

    /**
     * Set state of this circuit breaker.
     * Note that all usual processes to re-close or open the circuit are in progress.
     * <ul>
     *     <li>If set to {@link CircuitBreaker.State#OPEN}, a timer will set it to half open in a
     *     while</li>
     *     <li>If set to {@link CircuitBreaker.State#HALF_OPEN}, it may close after first
     *     successful request</li>
     *     <li>If set to {@link CircuitBreaker.State#CLOSED}, it may open again if requests
     *     fail</li>
     * </ul>
     * So a subsequent call to {@link #state()} may yield a different state than configured here
     *
     * @param newState state to configure
     */
    void state(State newState);

    /**
     * A circuit breaker can be in any of 3 possible states as defined by this enum.
     * The {@link CircuitBreaker.State#CLOSED} state is the normal one; an
     * {@link CircuitBreaker.State#OPEN} state
     * indicates the circuit breaker is blocking requests and
     * {@link CircuitBreaker.State#HALF_OPEN}
     * that a circuit breaker is transitioning to a {@link CircuitBreaker.State#CLOSED} state
     * provided enough successful requests are observed.
     */
    enum State {
        /**
         * Circuit is closed and requests are processed.
         */
        CLOSED,
        /**
         * Circuit is half open and some test requests are processed, others fail with
         * {@link CircuitBreakerOpenException}.
         */
        HALF_OPEN,
        /**
         * Circuit is open and all requests fail with {@link CircuitBreakerOpenException}.
         */
        OPEN
    }
}

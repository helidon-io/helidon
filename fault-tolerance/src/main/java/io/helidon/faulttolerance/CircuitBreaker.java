/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import io.helidon.common.LazyValue;

/**
 * CircuitBreaker protects a potentially failing endpoint from overloading and the application
 * from spending resources on those endpoints.
 * <p>
 * In case too many errors are detected, the circuit opens and all new requests fail with a
 * {@link io.helidon.faulttolerance.CircuitBreakerOpenException} for a period of time.
 * After this period, attempts are made to check if the service is up again - if so, the circuit closes
 * and requests can process as usual again.
 */
public interface CircuitBreaker extends FtHandler {
    /**
     * Builder to customize configuration of the breaker.
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Current breaker state.
     * As the state may change within nanoseconds, this is for information only.
     * @return current breaker state
     */
    State state();

    /**
     * Set state of this circuit breaker.
     * Note that all usual processes to re-close or open the circuit are in progress.
     * <ul>
     *     <li>If set to {@link State#OPEN}, a timer will set it to half open in a
     *     while</li>
     *     <li>If set to {@link State#HALF_OPEN}, it may close after first successful request</li>
     *     <li>If set to {@link State#CLOSED}, it may open again if requests fail</li>
     * </ul>
     * So a subsequent call to {@link #state()} may yield a different state than configured here
     * @param newState state to configure
     */
    void state(State newState);

    /**
     * A circuit breaker can be in any of 3 possible states as defined by this enum.
     * The {@link State#CLOSED} state is the normal one; an {@link State#OPEN} state
     * indicates the circuit breaker is blocking requests and {@link State#HALF_OPEN}
     * that a circuit breaker is transitioning to a {@link State#CLOSED} state
     * provided enough successful requests are observed.
     */
    enum State {
        /**
         * Circuit is closed and requests are processed.
         */
        CLOSED,
        /**
         * Circuit is half open and some test requests are processed, others fail with
         * {@link io.helidon.faulttolerance.CircuitBreakerOpenException}.
         */
        HALF_OPEN,
        /**
         * Circuit is open and all requests fail with {@link io.helidon.faulttolerance.CircuitBreakerOpenException}.
         */
        OPEN
    }

    /**
     * Fluent API builder for {@link io.helidon.faulttolerance.CircuitBreaker}.
     */
    class Builder implements io.helidon.common.Builder<CircuitBreaker> {
        private final Set<Class<? extends Throwable>> skipOn = new HashSet<>();
        private final Set<Class<? extends Throwable>> applyOn = new HashSet<>();
        // how long to transition from open to half-open
        private Duration delay = Duration.ofSeconds(5);
        // how many percents of failures will open the breaker
        private int ratio = 60;
        // how many successful calls will close a half-open breaker
        private int successThreshold = 1;
        // rolling window size to
        private int volume = 10;
        private LazyValue<? extends ScheduledExecutorService> executor = FaultTolerance.scheduledExecutor();
        private String name = "CircuitBreaker-" + System.identityHashCode(this);

        private Builder() {
        }

        @Override
        public CircuitBreaker build() {
            return new CircuitBreakerImpl(this);
        }

        /**
         * How long to wait before transitioning from open to half-open state.
         *
         * @param delay to wait
         * @return updated builder instance
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * How many failures out of 100 will trigger the circuit to open.
         * This is adapted to the {@link #volume(int)} used to handle the window of requests.
         * <p>If errorRatio is 40, and volume is 10, 4 failed requests will open the circuit.
         *
         * @param ratio percent of failure that trigger the circuit to open
         * @return updated builder instance
         * @see #volume(int)
         */
        public Builder errorRatio(int ratio) {
            this.ratio = ratio;
            return this;
        }

        /**
         * How many successful calls will close a half-open circuit.
         * Nevertheless the first failed call will open the circuit again.
         *
         * @param successThreshold number of calls
         * @return updated builder instance
         */
        public Builder successThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }

        /**
         * Rolling window size used to calculate ratio of failed requests.
         *
         * @param volume how big a window is used to calculate error errorRatio
         * @return updated builder instance
         * @see #errorRatio(int)
         */
        public Builder volume(int volume) {
            this.volume = volume;
            return this;
        }

        /**
         * These throwables will be considered failures, and all other will not.
         * <p>
         * Cannot be combined with {@link #skipOn}.
         *
         * @param classes to consider failures to calculate failure ratio
         * @return updated builder instance
         */
        @SafeVarargs
        public final Builder applyOn(Class<? extends Throwable>... classes) {
            applyOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addApplyOn);

            return this;
        }

        /**
         * Add a throwable to be considered a failure.
         *
         * @param clazz to consider failure to calculate failure ratio
         * @return updated builder instance
         * @see #applyOn
         */
        public Builder addApplyOn(Class<? extends Throwable> clazz) {
            this.applyOn.add(clazz);
            return this;
        }

        /**
         * These throwables will not be considered failures, all other will.
         * <p>
         * Cannot be combined with {@link #applyOn}. </p>
         *
         * @param classes to consider successful
         * @return updated builder instance
         */
        @SafeVarargs
        public final Builder skipOn(Class<? extends Throwable>... classes) {
            skipOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addSkipOn);

            return this;
        }

        /**
         * This throwable will not be considered failure.
         *
         * @param clazz to consider successful
         * @return updated builder instance
         */
        public Builder addSkipOn(Class<? extends Throwable> clazz) {
            this.skipOn.add(clazz);
            return this;
        }

        /**
         * Executor service to schedule future tasks.
         * By default uses an executor configured on
         * {@link io.helidon.faulttolerance.FaultTolerance#scheduledExecutor(java.util.function.Supplier)}.
         *
         * @param scheduledExecutor executor to use
         * @return updated builder instance
         */
        public Builder executor(ScheduledExecutorService scheduledExecutor) {
            this.executor = LazyValue.create(scheduledExecutor);
            return this;
        }

        /**
         * A name assigned for debugging, error reporting or configuration purposes.
         *
         * @param name the name
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        LazyValue<? extends ScheduledExecutorService> executor() {
            return executor;
        }

        Set<Class<? extends Throwable>> skipOn() {
            return skipOn;
        }

        Set<Class<? extends Throwable>> applyOn() {
            return applyOn;
        }

        Duration delay() {
            return delay;
        }

        int errorRatio() {
            return ratio;
        }

        int successThreshold() {
            return successThreshold;
        }

        int volume() {
            return volume;
        }

        String name() {
            return name;
        }
    }
}

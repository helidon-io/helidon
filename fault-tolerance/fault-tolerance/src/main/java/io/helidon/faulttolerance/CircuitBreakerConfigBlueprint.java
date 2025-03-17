/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

//@ConfigDriven.ConfigBean
//@ConfigDriven.WantDefault
//@ConfigDriven.Repeatable
@Prototype.Blueprint(decorator = CircuitBreakerConfigBlueprint.BuilderDecorator.class)
@Prototype.Configured("fault-tolerance.circuit-breakers")
interface CircuitBreakerConfigBlueprint extends Prototype.Factory<CircuitBreaker> {
    int DEFAULT_ERROR_RATIO = 60;
    int DEFAULT_SUCCESS_THRESHOLD = 1;
    int DEFAULT_VOLUME = 10;

    Optional<String> name();

    /**
     * How long to wait before transitioning from open to half-open state.
     *
     * @return delay
     */
    @Option.Configured
    @Option.Default("PT5S")
    Duration delay();

    /**
     * How many failures out of 100 will trigger the circuit to open.
     * This is adapted to the {@link #volume()} used to handle the window of requests.
     * <p>If errorRatio is 40, and volume is 10, 4 failed requests will open the circuit.
     * Default is {@value #DEFAULT_ERROR_RATIO}.
     *
     * @return percent of failure that trigger the circuit to open
     * @see #volume()
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_ERROR_RATIO)
    int errorRatio();

    /**
     * Rolling window size used to calculate ratio of failed requests.
     * Default is {@value #DEFAULT_VOLUME}.
     *
     * @return how big a window is used to calculate error errorRatio
     * @see #errorRatio()
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_VOLUME)
    int volume();

    /**
     * How many successful calls will close a half-open circuit.
     * Nevertheless, the first failed call will open the circuit again.
     * Default is {@value #DEFAULT_SUCCESS_THRESHOLD}.
     *
     * @return number of calls
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_SUCCESS_THRESHOLD)
    int successThreshold();

    /**
     * Executor service to schedule future tasks.
     *
     * @return executor to use
     */
    Optional<ExecutorService> executor();

    /**
     * These throwables will not be considered failures, all other will.
     *
     * @return throwable classes to not be considered a failure
     * @see #applyOn()
     */
    @Option.Singular
    Set<Class<? extends Throwable>> skipOn();

    /**
     * These throwables will be considered failures.
     *
     * @return throwable classes to be considered a failure
     * @see #skipOn()
     */
    @Option.Singular
    Set<Class<? extends Throwable>> applyOn();

    /**
     * Flag to enable metrics for this instance. The value of this flag is
     * combined with the global config entry
     * {@link io.helidon.faulttolerance.FaultTolerance#FT_METRICS_DEFAULT_ENABLED}.
     * If either of these flags is {@code true}, then metrics will be enabled
     * for the instance.
     *
     * @return metrics enabled flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();

    class BuilderDecorator implements Prototype.BuilderDecorator<CircuitBreakerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(CircuitBreakerConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }
}

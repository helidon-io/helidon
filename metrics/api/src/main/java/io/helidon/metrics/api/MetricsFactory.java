/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Behavior of implementations of the Helidon metrics API.
 * <p>
 *     An implementation of this interface provides instance methods for each
 *     of the static methods on the Helidon metrics API interfaces. The prefix of each method
 *     here identifies the interface that bears the corresponding static method. For example,
 *     {@link #timerStart(io.helidon.metrics.api.MeterRegistry)} corresponds to the static
 *     {@link io.helidon.metrics.api.Timer#start(io.helidon.metrics.api.MeterRegistry)} method.
 * </p>
 * <p>
 *     ALso, various static methods create or return previously-created instances.
 * </p>
 * <p>
 *     Note that this is not intended to be the interface which developers use to work with Helidon metrics.
 *     Instead they should use the {@link io.helidon.metrics.api.Metrics} interface and its static convenience methods
 *     or use {@link io.helidon.metrics.api.Metrics#globalRegistry()} and use the returned
 *     {@link io.helidon.metrics.api.MeterRegistry} directly.
 * </p>
 * <p>
 *     Rather, implementations of Helidon metrics implement this interface and various internal parts of Helidon metrics,
 *     notably the static methods on {@link io.helidon.metrics.api.Metrics}, delegate to the highest-weight
 *     implementation of this interface.
 * </p>
 */
public interface MetricsFactory {

    static MetricsFactory getInstance() {
        return MetricsFactoryManager.getInstance();
    }

    /**
     * Returns the global meter registry.
     *
     * @return the global meter registry
     */
    MeterRegistry globalRegistry();

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.Counter}.
     *
     * @param name name of the counter
     * @return counter builder
     */
    Counter.Builder counterBuilder(String name);

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.DistributionStatisticsConfig}.
     *
     * @return statistics config builder
     */
    DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder();

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name name of the summary
     * @return summary builder
     */
    DistributionSummary.Builder distributionSummaryBuilder(String name);

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.Gauge}.
     *
     * @param name name of the gauge
     * @param stateObject object which maintains the value to be exposed via the gauge
     * @param fn function which, when applied to the state object, returns the gauge value
     * @return gauge builder
     * @param <T> type of the state object
     */
    <T> Gauge.Builder<T> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn);

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.HistogramSupport}.
     *
     * @return summary builder
     */
    HistogramSupport.Builder histogramSupportBuilder();

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.Timer}.
     *
     * @param name name of the timer
     * @return timer builder
     */
    Timer.Builder timerBuilder(String name);

    /**
     * Returns a {@link io.helidon.metrics.api.Timer.Sample} for measuring a duration using
     * the system default {@link io.helidon.metrics.api.Clock}.
     *
     * @return new sample
     */
    Timer.Sample timerStart();

    /**
     * Returns a {@link io.helidon.metrics.api.Timer.Sample} for measuring a duration, using the
     * clock associated with the specified {@link io.helidon.metrics.api.MeterRegistry}.
     *
     * @param registry the meter registry whose {@link io.helidon.metrics.api.Clock} is to be used
     * @return new sample with the start time recorded
     */
    Timer.Sample timerStart(MeterRegistry registry);

    /**
     * Returns a {@link io.helidon.metrics.api.Timer.Sample} for measuring a duration using
     * the specified {@link io.helidon.metrics.api.Clock}.
     *
     * @param clock the clock to use for measuring the duration
     * @return new sample
     */
    Timer.Sample timerStart(Clock clock);

    /**
     * Creates a {@link io.helidon.metrics.api.Meter.Id} from the specified name and tags.
     * @param name name to use in the ID
     * @param tags tags to use in the ID
     * @return new meter ID
     */
    Meter.Id idOf(String name, Iterable<Tag> tags);

    /**
     * Creates a {@link io.helidon.metrics.api.Meter.Id} from the specified name.
     *
     * @param name name to use in the ID
     * @return new meter ID
     */
    default Meter.Id idOf(String name) {
        return idOf(name, Set.of());
    }

    /**
     * Creates a {@link io.helidon.metrics.api.Tag} from the specified key and value.
     *
     * @param key   tag key
     * @param value tag value
     * @return new {@code Tag} instance
     */
    Tag tagOf(String key, String value);

    /**
     * Returns an empty histogram snapshot with the specified aggregate values.
     *
     * @param count count
     * @param total total
     * @param max max value
     * @return histogram snapshot
     */
    HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max);
}

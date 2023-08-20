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

import java.util.function.ToDoubleFunction;

import io.helidon.common.config.Config;

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
 *     Also, various static methods create new instances or return previously-created ones.
 * </p>
 * <p>
 *     Note that this is not intended to be the interface which developers use to work with Helidon metrics.
 *     Instead use
 *     <ul>
 *         <li>the {@link io.helidon.metrics.api.Metrics} interface and its static convenience methods,</li>
 *         <li>the static methods on the various interfaces in the API, or</li>
 *         <li>{@link io.helidon.metrics.api.Metrics#globalRegistry()} and use the returned
 *      {@link io.helidon.metrics.api.MeterRegistry} directly</li>
 *     </ul>
 * <p>
 *     Rather, implementations of Helidon metrics implement this interface and various internal parts of Helidon metrics,
 *     notably the static methods on {@link io.helidon.metrics.api.Metrics}, delegate to the highest-weight
 *     implementation of this interface.
 * </p>
 */
public interface MetricsFactory {

    /**
     * Returns the most-recently created implementation or, if none, a new one from a highest-weight provider available at
     * runtime and using the {@value MetricsConfig.Builder#METRICS_CONFIG_KEY} section from the
     * {@link io.helidon.common.config.GlobalConfig}.
     *
     * @return current or new metrics factory
     */
    static MetricsFactory getInstance() {
        return MetricsFactoryManager.getInstance();
    }

    /**
     * Returns a new instance from a highest-weight provider available at runtime using the provided
     * {@link io.helidon.common.config.Config} to set up the factory.
     * @param rootConfig top-level config node
     * @return new instance configured as directed
     */
    static MetricsFactory getInstance(Config rootConfig) {
        return MetricsFactoryManager.getInstance(rootConfig);
    }

    /**
     * Returns the global meter registry.
     *
     * @return the global meter registry
     */
    MeterRegistry globalRegistry();

    /**
     * Creates a new {@link io.helidon.metrics.api.MeterRegistry} using the provided metrics config.
     *
     * @param metricsConfig metrics configuration which influences the new registry
     * @return new meter registry
     */
    MeterRegistry createMeterRegistry(MetricsConfig metricsConfig);

    /**
     * Creates a new {@link io.helidon.metrics.api.MeterRegistry} using the provided {@link io.helidon.metrics.api.Clock} and
     * metrics config.
     *
     * @param clock default clock to associate with the meter registry
     * @param metricsConfig metrics configuration which influences the new registry
     * @return new meter registry
     */
    MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig);

    /**
     * Returns the system {@link io.helidon.metrics.api.Clock} from the
     * underlying metrics implementation.
     *
     * @return the system clock
     */
    Clock clockSystem();

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.Counter}.
     *
     * @param name name of the counter
     * @return counter builder
     */
    Counter.Builder counterBuilder(String name);

    /**
     * Creates a builder for a functional {@link io.helidon.metrics.api.Counter}, essentially a counter-style
     * wrapper around an external object.
     *
     * @param name name of the counter
     * @param stateObject object which provides the counter value
     * @param fn function which, when applied to the state object, yields the counter value
     * @return counter builder
     * @param <T> type of the state object
     */
    <T> FunctionalCounter.Builder<T> functionalCounterBuilder(String name, T stateObject, ToDoubleFunction<T> fn);

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
     * @param configBuilder distribution stats config the summary should use
     * @return summary builder
     */
    DistributionSummary.Builder distributionSummaryBuilder(String name, DistributionStatisticsConfig.Builder configBuilder);

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
     * Creates a {@link io.helidon.metrics.api.Tag} from the specified key and value.
     *
     * @param key   tag key
     * @param value tag value
     * @return new {@code Tag} instance
     */
    Tag tagCreate(String key, String value);

    /**
     * Returns an empty histogram snapshot with the specified aggregate values.
     *
     * @param count count
     * @param total total
     * @param max max value
     * @return histogram snapshot
     */
    HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max);

    /**
     * Returns a no-op {@link io.helidon.metrics.api.Meter} of the type implied by the builder's type, initialized with
     * the builder's name and other required parameters.
     *
     * @param builder original builder
     * @return corresponding no-op meter
     * @param <B> type of the builder
     * @param <M> type of the meter the builder produces
     */
    default <M extends Meter, B extends Meter.Builder<B, M>> Meter noOpMeter(B builder) {
        if (builder instanceof Counter.Builder cb) {
            return NoOpMeter.Counter.builder(cb.name()).build();
        }
        if (builder instanceof FunctionalCounter.Builder fcb) {
            return NoOpMeter.FunctionalCounter.builder(fcb.name(), fcb.stateObject(), fcb.fn()).build();
        }
        if (builder instanceof DistributionSummary.Builder sb) {
            return NoOpMeter.DistributionSummary.builder(sb.name()).build();
        }
        if (builder instanceof Gauge.Builder gb) {
            return NoOpMeter.Gauge.builder(gb.name(), gb.stateObject(), gb.fn()).build();
        }
        if (builder instanceof Timer.Builder tb) {
            return NoOpMeter.Timer.builder(tb.name()).build();
        }
        throw new IllegalArgumentException("Unrecognized meter builder type " + builder.getClass().getName());
    }
}

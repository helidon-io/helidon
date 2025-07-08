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
package io.helidon.metrics.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.common.config.Config;

/**
 * The basic contract for implementations of the Helidon metrics API, mostly acting as a factory for
 * meter <em>builders</em> rather than for meters themselves.
 * <p>
 * This is not intended to be the interface which developers use to work with Helidon metrics. Instead use
 *     <ul>
 *         <li>the {@link io.helidon.metrics.api.Metrics} interface and its static convenience methods,</li>
 *         <li>the static methods on the various meter interfaces in the API (such as {@link io.helidon.metrics.api.Timer},
 *         or</li>
 *         <li>{@link io.helidon.metrics.api.Metrics#globalRegistry()} and use the returned
 *      {@link io.helidon.metrics.api.MeterRegistry} directly</li>
 *     </ul>
 * <p>
 * An implementation of this interface provides instance methods for each
 * of the static methods on the Helidon metrics API interfaces. The prefix of each method
 * here identifies the interface that bears the corresponding static method. For example,
 * {@link #timerStart(io.helidon.metrics.api.MeterRegistry)} corresponds to the static
 * {@link io.helidon.metrics.api.Timer#start(io.helidon.metrics.api.MeterRegistry)} method.
 * <p>
 * Also, various static methods create new instances or return previously-created ones.
 */
public interface MetricsFactory {

    /**
     * Returns the most-recently created implementation or, if none, a new one from a highest-weight provider available at
     * runtime and using the {@value MetricsConfigBlueprint#METRICS_CONFIG_KEY} section from the
     * current config.
     *
     * @return current or new metrics factory
     */
    static MetricsFactory getInstance() {
        return MetricsFactoryManager.getMetricsFactory();
    }

    /**
     * Returns a new metrics factory instance from a highest-weight provider using the provided
     * {@link io.helidon.common.config.Config} to set up the metrics factory and saving the resulting metrics factory
     * as the current one, returned by {@link #getInstance()}}.
     *
     * @param metricsConfigNode metrics config node
     * @return new instance configured as directed
     */
    static MetricsFactory getInstance(Config metricsConfigNode) {
        return MetricsFactoryManager.getMetricsFactory(metricsConfigNode);
    }

    /**
     * Closes all {@link io.helidon.metrics.api.MetricsFactory} instances.
     *
     * <p>
     *     Applications do not normally need to invoke this method.
     * </p>
     */
    static void closeAll() {
        MetricsFactoryManager.closeAll();
    }

    /**
     * Closes this metrics factory.
     *
     * <p>
     *     Applications do not normally need to invoke this method.
     * </p>
     */
    void close();

    /**
     * Returns the global {@link io.helidon.metrics.api.MeterRegistry} for this metrics factory.
     *
     * <p>
     * The metric factory creates its global registry on-demand using
     * {@link #getInstance()}.{@link #createMeterRegistry(MetricsConfig)} with a
     * {@link MetricsConfig} instance derived from the root {@link io.helidon.common.config.Config} most recently passed to
     * {@link #getInstance(io.helidon.common.config.Config)}, or if none then the config from
     * current {@link io.helidon.common.config.Config}.
     *
     * @return the global meter registry
     */
    MeterRegistry globalRegistry();

    /**
     * Creates a new global registry according to the configuration and returns it.
     *
     * @param metricsConfig configuration to control the meter registry
     * @return meter registry
     */
    MeterRegistry globalRegistry(MetricsConfig metricsConfig);


    /**
     * Returns the global {@link io.helidon.metrics.api.MeterRegistry} enrolling the specified listeners to the meter registry.
     *
     * @param onAddListener    invoked whenever a new meter is added to the returned meter registry
     * @param onRemoveListener invoked whenever a meter is removed from the returned meter registry
     * @param backfill         whether the meter registry should invoke the on-add listener for meters already present in an
     *                         existing global registry
     * @return on-demand global registry with the indicated listeners added
     */
    MeterRegistry globalRegistry(Consumer<Meter> onAddListener, Consumer<Meter> onRemoveListener, boolean backfill);

    /**
     * Returns the {@link io.helidon.metrics.api.MetricsConfig} instance used to initialize the metrics factory.
     *
     * @return metrics config used to create the metrics factory
     */
    MetricsConfig metricsConfig();

    /**
     * Returns a builder for creating a new {@link io.helidon.metrics.api.MeterRegistry}.
     *
     * @return the new builder
     * @param <B> specific type of the builder
     * @param <M> specific type of the registry
     */
    <B extends MeterRegistry.Builder<B, M>, M extends MeterRegistry> B meterRegistryBuilder();

    /**
     * Creates a new {@link io.helidon.metrics.api.MeterRegistry} using the provided metrics config.
     *
     * @param metricsConfig metrics configuration which influences the new registry
     * @return new meter registry
     */
    MeterRegistry createMeterRegistry(MetricsConfig metricsConfig);

    /**
     * Creates a new {@link io.helidon.metrics.api.MeterRegistry} using the provided metrics config and enrolling the specified
     * listeners with the returned meter registry.
     *
     * @param metricsConfig    metrics configuration which influences the new registry
     * @param onAddListener    invoked whenever a new meter is added to the meter registry
     * @param onRemoveListener invoked whenever a new meter is removed from the meter registry
     * @return new meter registry with the listeners enrolled
     */
    MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                      Consumer<Meter> onAddListener,
                                      Consumer<Meter> onRemoveListener);

    /**
     * Creates a new {@link io.helidon.metrics.api.MeterRegistry} using the provided {@link io.helidon.metrics.api.Clock} and
     * {@link io.helidon.metrics.api.MetricsConfig}.
     *
     * @param clock         default clock to associate with the meter registry
     * @param metricsConfig metrics configuration which influences the new registry
     * @return new meter registry
     */
    MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig);

    /**
     * Creates a new {@link io.helidon.metrics.api.MeterRegistry} using the provided {@link io.helidon.metrics.api.Clock} and
     * {@link io.helidon.metrics.api.MetricsConfig} and enrolling the specified listners with the new meter registry.
     *
     * @param clock            clock to associate with the meter registry
     * @param metricsConfig    metrics config which influences the new registry
     * @param onAddListener    invoked whenever a new meter is added to the meter registry
     * @param onRemoveListener invoked whenever a new meter is removed from the meter registry
     * @return new meter registry
     */
    MeterRegistry createMeterRegistry(Clock clock,
                                      MetricsConfig metricsConfig,
                                      Consumer<Meter> onAddListener,
                                      Consumer<Meter> onRemoveListener);

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
     * @param name        name of the counter
     * @param stateObject object which provides the counter value
     * @param fn          function which, when applied to the state object, yields the counter value
     * @param <T>         type of the state object
     * @return counter builder
     */
    <T> FunctionalCounter.Builder<T> functionalCounterBuilder(String name, T stateObject, Function<T, Long> fn);

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.DistributionStatisticsConfig}.
     *
     * @return statistics config builder
     */
    DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder();

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name          name of the summary
     * @param configBuilder distribution stats config the summary should use
     * @return summary builder
     */
    DistributionSummary.Builder distributionSummaryBuilder(String name, DistributionStatisticsConfig.Builder configBuilder);

    /**
     * Creates a builder for a state-based {@link io.helidon.metrics.api.Gauge}.
     *
     * @param name        name of the gauge
     * @param stateObject object which maintains the value to be exposed via the gauge
     * @param fn          function which, when applied to the state object, returns the gauge value
     * @param <T>         type of the state object
     * @return gauge builder
     */
    <T> Gauge.Builder<Double> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn);

    /**
     * Creates a builder for a {@link io.helidon.metrics.api.Gauge} based on a supplier of a subtype of {@link Number}.
     *
     * @param name     gauge name
     * @param supplier supplier for an instance of the specified subtype of {@code Number}
     * @param <N>      subtype of {@code Number} which the supplier providers
     * @return new builder
     */
    <N extends Number> Gauge.Builder<N> gaugeBuilder(String name, Supplier<N> supplier);

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
     * @param max   max value
     * @return histogram snapshot
     */
    HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max);

    /**
     * Returns a no-op {@link io.helidon.metrics.api.Meter} of the type implied by the builder's runtime type, initialized with
     * the builder's name and other required parameters.
     *
     * @param builder original builder
     * @return corresponding no-op meter
     */
    default Meter noOpMeter(Meter.Builder<?, ?> builder) {
        NoOpMeter.Builder<?, ?> noOpBuilder;
        if (builder instanceof Counter.Builder cb) {
            noOpBuilder = NoOpMeter.Counter.builder(cb.name());
        } else if (builder instanceof FunctionalCounter.Builder fcb) {
            noOpBuilder = NoOpMeter.FunctionalCounter.builder(fcb.name(), fcb.stateObject(), fcb.fn());
        } else if (builder instanceof DistributionSummary.Builder sb) {
            noOpBuilder = NoOpMeter.DistributionSummary.builder(sb.name());
        } else if (builder instanceof Gauge.Builder gb) {
            noOpBuilder = NoOpMeter.Gauge.builder(gb.name(), gb.supplier());
        } else if (builder instanceof Timer.Builder tb) {
            noOpBuilder = NoOpMeter.Timer.builder(tb.name());
        } else {
            throw new IllegalArgumentException("Unrecognized meter builder type " + builder.getClass().getName());
        }
        SystemTagsManager.instance()
                .effectiveScope(builder.scope())
                .ifPresent(noOpBuilder::scope);
        return noOpBuilder.build();
    }
}

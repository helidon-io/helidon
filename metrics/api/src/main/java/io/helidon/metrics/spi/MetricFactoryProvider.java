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
package io.helidon.metrics.spi;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

/**
 * Factory for creating implementation instances of the Helidon metrics API.
 * <p>
 *     An implementation of this interface provides instance methods for each
 *     of the static methods on the Helidon metrics API interfaces. The prefix of each method
 *     here identifies the interface that bears the corresponding static method. For example,
 *     {@link #timerStart(io.helidon.metrics.api.MeterRegistry)} corresponds to the static
 *     {@link Timer#start(io.helidon.metrics.api.MeterRegistry)} method.
 * </p>
 */
public interface MetricFactoryProvider {

    /**
     * Returns the global meter registry.
     *
     * @return the global meter registry
     */
    MeterRegistry globalRegistry();

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
    Tag tagOf(String key, String value);

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing counter.
     */
    default Counter metricsCounter(String name, Iterable<Tag> tags) {
        return globalRegistry().counter(name, tags);
    }

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing counter.
     */
    default Counter metricsCounter(String name, String... tags) {
        return globalRegistry().counter(name, tags);
    }

    /**
     * Tracks a monotonically increasing value as maintained by an external variable.
     *
     * @param name meter name
     * @param tags tags for further identifying the meter
     * @param target object which, when the function is applied, provides the counter value
     * @param fn function which obtains the counter value from the target object
     * @return counter
     * @param <T> type of the target object
     */
    <T> Counter metricsCounter(String name, Iterable<Tag> tags, T target, Function<T, Double> fn);

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing distribution summary.
     */
    default DistributionSummary metricsSummary(String name, Iterable<Tag> tags) {
        return globalRegistry().summary(name, tags);
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name name of the new meter
     * @param tags tags for identifying the new meter
     * @return new {@code DistributionSummary}
     */
    default DistributionSummary metricsSummary(String name, String... tags) {
        return globalRegistry().summary(name, tags);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing timer.
     */
    default Timer metricsTimer(String name, Iterable<Tag> tags) {
        return globalRegistry().timer(name, tags);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing timer.
     */
    default Timer metricsTimer(String name, String... tags) {
        return globalRegistry().timer(name, tags);
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code valueFunction} is applied. The registration will keep a weak reference to the object so it will
     * not prevent garbage collection. Applying {@code valueFunction} on the object should be thread safe.
     * <p>
     * If multiple gauges are registered with the same id, then the values will be aggregated and
     * the sum will be reported. For example, registering multiple gauges for active threads in
     * a thread pool with the same id would produce a value that is the overall number
     * of active threads. For other behaviors, manage it on the user side and avoid multiple
     * registrations.
     *
     * @param name          Name of the gauge being registered.
     * @param tags          Sequence of dimensions for breaking down the name.
     * @param obj           Object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T> T metricsGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> valueFunction) {
        return globalRegistry().gauge(name, tags, obj, valueFunction);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Number> T metricsGauge(String name, Iterable<Tag> tags, T number) {
        return globalRegistry().gauge(name, tags, number);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Number> T metricsGauge(String name, T number) {
        return globalRegistry().gauge(name, number);
    }

    /**
     * Register a gauge that reports the value of the object.
     *
     * @param name          Name of the gauge being registered.
     * @param obj           Object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.F
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T> T metricsGauge(String name, T obj, ToDoubleFunction<T> valueFunction) {
        return globalRegistry().gauge(name, obj, valueFunction);
    }

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

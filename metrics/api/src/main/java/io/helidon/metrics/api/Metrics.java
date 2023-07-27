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

/**
 * A main entry point to the Helidon metrics implementation, allowing access to the global meter registry and providing shortcut
 * methods to register and locate meters in the global registry and remove meters from it.
 */
public interface Metrics {

    /**
     * Returns the global meter registry.
     *
     * @return the global meter registry
     */
    static MeterRegistry globalRegistry() {
        return MetricFactoryManager.INSTANCE.get().globalRegistry();
    }

    /**
     * Registers a new or locates a previously-registered counter, using the global registry, which tracks a monotonically
     * increasing value.
     *
     * @param name counter name
     * @param tags further identification of the counter
     * @return new or previously-registered counter
     */
    static Counter counter(String name, Iterable<Tag> tags) {
        return globalRegistry().counter(name, tags);
    }

    /**
     * Registers a new or locates a previously-registered counter, using the global registry, which tracks a monotonically
     * increasing value.
     *
     * @param name counter name
     * @param tags further identification of the counter; MUST be an even number of arguments representing key/value pairs
     *             of tags
     * @return new or previously-registered counter
     */
    static Counter counter(String name, String... tags) {
        return globalRegistry().counter(name, tags);
    }

    /**
     * Registers a new or locates a previously-registered counter, using the global registry, which tracks a monotonically
     * increasing value that is maintained by an external object, not a counter furnished by the meter registry itself.
     *
     * <p>
     *     The counter returned rejects attempts to increment its value because the external object, not the counter itself,
     *     maintains the value.
     * </p>
     *
     * @param name counter name
     * @param tags further identification of the counter
     * @param target object which, when the function is applied, yields the counter value
     * @param fn function which produces the counter value
     * @return new or existing counter
     * @param <T> type of the object which furnishes the counter value
     */
    static <T> Counter counter(String name, Iterable<Tag> tags, T target, ToDoubleFunction<T> fn) {
        return globalRegistry().counter(name, tags, target, fn);
    }

    /**
     * Registers a new or locates a previously-registered distribution summary, using the global registry, which measures the
     * distribution of samples.
     *
     * @param name summary name
     * @param tags further identification of the summary
     * @return new or previously-registered distribution summary
     */
    static DistributionSummary summary(String name, Iterable<Tag> tags) {
        return globalRegistry().summary(name, tags);
    }

    /**
     * Registers a new or locates a previously-registered distribution summary, using the global registry, which measures the
     * distribution of samples.
     *
     * @param name summary name
     * @param tags further identification of the summary; MUST be an even number of arguments representing key/value pairs
     *             of tags
     * @return new or previously-registered distribution summary
     */
    static DistributionSummary summary(String name, String... tags) {
        return globalRegistry().summary(name, tags);
    }

    /**
     * Registers a new or locates a previously-registered timer, using the global registry, which measures the time taken for
     * short tasks and the count of those tasks.
     *
     * @param name timer name
     * @param tags further identification of the timer
     * @return new or previously-registered timer
     */
    static Timer timer(String name, Iterable<Tag> tags) {
        return globalRegistry().timer(name, tags);
    }

    /**
     * Registers a new or locates a previously-registered timer, using the global registry,  which measures the time taken for
     * short tasks and the count of those tasks.
     *
     * @param name timer name
     * @param tags further identification of the timer; MUST be an even number of arguments representing key/value pairs of tags.
     * @return new or previously-registered timer
     */
    static Timer timer(String name, String... tags) {
        return globalRegistry().timer(name, tags);
    }

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge}, using the global registry, that reports the double value
     * maintained by the specified object and exposed by the object by applying the specified function.
     *
     * @param name name of the gauge
     * @param tags further identification of the gauge
     * @param obj object which exposes the gauge value
     * @param valueFunction function which, when applied to the object, yields the gauge value
     * @param <T> type of the state object which maintains the gauge's value
     * @return state object
     */
    static <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> valueFunction) {
        return globalRegistry().gauge(name, tags, obj, valueFunction);
    }

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge}, using the global registry, which wraps a specific
     * {@link java.lang.Number} instance.
     *
     * @param name name of the gauge
     * @param tags further identification of the gauge
     * @param number thread-safe implementation of the specified subtype of {@link java.lang.Number} which is the gauge's value
     * @param <N> specific subtype of {@code Number} which the wrapped object exposes
     * @return {@code number} wrapped by this gauge
     */
    static <N extends Number> N gauge(String name, Iterable<Tag> tags, N number) {
        return globalRegistry().gauge(name, tags, number);
    }

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge}, using the global registry, which wraps a specific
     * {@link java.lang.Number} instance.
     *
     * @param name name of the gauge
     * @param number thread-safe implementation of the specified subtype of {@link java.lang.Number} which is the gauge's value
     * @param <N> specific subtype of {@code Number} which the wrapped object exposes
     * @return {@code number} wrapped by this gauge
     */
    static <N extends Number> N gauge(String name, N number) {
        return globalRegistry().gauge(name, number);
    }

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge}, using the global registry, that reports the double value
     * maintained by the specified object and exposed by the object by applying the specified function.
     *
     * @param name name of the gauge
     * @param obj object which exposes the gauge value
     * @param valueFunction function which, when applied to the object, yields the gauge value
     * @param <T> type of the state object which maintains the gauge's value
     * @return state object
     */
    static <T> T gauge(String name, T obj, ToDoubleFunction<T> valueFunction) {
        return globalRegistry().gauge(name, obj, valueFunction);
    }
}

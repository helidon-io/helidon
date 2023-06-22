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
package io.helidon.metrics.api.spi;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Factory for creating a Helidon-specific metric given its name, metadata, and tags.
 */
public interface MetricFactory {

    /**
     * Creates a counter.
     *
     * @param scope registry scope
     * @param metadata metadata describing the counter
     * @param tags tags further identifying the counter
     * @return new counter
     */
    Counter counter(String scope, Metadata metadata, Tag... tags);

    /**
     * Creates a timer.
     *
     * @param scope registry scope
     * @param metadata metadata describing the timer
     * @param tags tags further identifying the timer
     * @return new timer
     */
    Timer timer(String scope, Metadata metadata, Tag... tags);

    /**
     * Creates a histogram/distribution summary.
     *
     * @param scope registry scope
     * @param metadata metadata describing the summary
     * @param tags tags further identifying the summary
     * @return new summary
     */
    Histogram summary(String scope, Metadata metadata, Tag... tags);

    /**
     * Creates a gauge.
     *
     * @param scope registry scope
     * @param metadata metadata describing the gauge
     * @param supplier supplier of the gauge value
     * @param tags tags further identifying the gauge
     * @return new gauge
     * @param <N> gauge's value type
     */
    <N extends Number> Gauge<N> gauge(String scope, Metadata metadata, Supplier<N> supplier, Tag... tags);

    /**
     * Creates a gauge.
     *
     * @param scope registry scope
     * @param metadata metadata describing the gauge
     * @param target object which dispenses the gauge value
     * @param fn function which, when applied to the target, reveals the gauge value
     * @param tags tags further identifying the gauge
     * @return new gauge
     * @param <N> gauge's value type
     * @param <T> type of the target which reveals the value
     */
    <N extends Number, T> Gauge<N> gauge(String scope,
                                         Metadata metadata,
                                         T target,
                                         Function<T, N> fn,
                                         Tag... tags);

    /**
     * Creates a gauge.
     *
     * @param scope registry scope
     * @param metadata metadata describing the gauge
     * @param target object which dispenses the gauge value
     * @param fn function which, when applied to the target, reveals the gauge value as a double
     * @param tags tags further identifying the gauge
     * @return new gauge
     * @param <T> type of the target which reveals the value

     */
    <T> Gauge<Double> gauge(String scope,
                            Metadata metadata,
                            T target,
                            ToDoubleFunction<T> fn,
                            Tag... tags);
}

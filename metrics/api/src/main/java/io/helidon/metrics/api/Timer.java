/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Records timing information about large numbers of short-running events (e.g., HTTP requests).
 */
public interface Timer extends Meter, HistogramSupport {

    /**
     * Creates a builder for a new {@link io.helidon.metrics.api.Timer}.
     *
     * @param name timer name
     * @return new builder
     */
    static Builder builder(String name) {
        return MetricsFactory.getInstance().timerBuilder(name);
    }

    /**
     * Starts a timing sample using the default system clock.
     *
     * @return new sample
     */
    static Sample start() {
        return MetricsFactory.getInstance().timerStart();
    }

    /**
     * Starts a timing sample using the clock associated with the specified {@link io.helidon.metrics.api.MeterRegistry}.
     *
     * @param registry the meter registry whose clock is to be used for measuring the interval
     * @return new sample with start time recorded
     */
    static Sample start(MeterRegistry registry) {
        return MetricsFactory.getInstance().timerStart(registry);
    }

    /**
     * Starts a timing sample using the specified clock.
     *
     * @param clock a clock to be used
     * @return new sample with start time recorded
     */
    static Sample start(Clock clock) {
        return MetricsFactory.getInstance().timerStart(clock);
    }

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount duration of a single event being measured by this timer. If the amount is less than 0
     *               the value will be dropped
     * @param unit   time unit for the amount being recorded
     */
    void record(long amount, TimeUnit unit);

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param duration duration of a single event being measured by this timer
     */
    void record(Duration duration);

    /**
     * Executes the {@link java.util.function.Supplier} {@code f} and records the time spent invoking the function.
     *
     * @param f   function to be timed
     * @param <T> return type of the {@link java.util.function.Supplier}
     * @return return value from invoking the function {@code f}
     */
    <T> T record(Supplier<T> f);

    /**
     * Executes the {@link java.util.concurrent.Callable} {@code f} and records the time spent it, returning the
     * callable's result.
     *
     * @param f   callable to be timed
     * @param <T> return type of the {@link java.util.concurrent.Callable}
     * @return return value from invoking the callable {@code f}
     * @throws Exception exception escaping from the callable
     */
    <T> T record(Callable<T> f) throws Exception;

    /**
     * Executes the {@link java.lang.Runnable} {@code f} and records the time it takes.
     *
     * @param f runnable to be timed
     */
    void record(Runnable f);

    /**
     * Wraps a {@link Runnable} so that it will be timed every time it is invoked via the return value from this method.
     *
     * @param f runnable to time when it is invoked
     * @return the wrapped runnable
     */
    Runnable wrap(Runnable f);

    /**
     * Wraps a {@link Callable} so that it is will be timed every time it is invoked via the return value from this method.
     *
     * @param f   callable to time when it is invoked
     * @param <T> return type of the callable
     * @return the wrapped callable
     */
    <T> Callable<T> wrap(Callable<T> f);

    /**
     * Wraps a {@link Supplier} so that it will be timed every time it is invoked via the return value from this method.
     *
     * @param f   {@code Supplier} to time when it is invoked
     * @param <T> return type of the {@code Supplier} result
     * @return the wrapped supplier
     */
    <T> Supplier<T> wrap(Supplier<T> f);

    /**
     * Returns the current count of completed events measured by the timer.
     *
     * @return number of events recorded by the timer
     */
    long count();

    /**
     * Returns the total time, expressed in the specified units, consumed by completed events
     * measured by the timer.
     *
     * @param unit time unit in which to express the total accumulated time
     * @return total time of recorded events
     */
    double totalTime(TimeUnit unit);

    /**
     * Returns the average time, expressed in the specified units, consumed by completed events
     * measured by the timer.
     *
     * @param unit time unit in which to express the mean
     * @return average for all events recorded by this timer
     */
    double mean(TimeUnit unit);

    /**
     * Returns the maximum value, expressed in the specified units, consumed by a completed event
     * measured by the timer.
     *
     * @param unit time unit in which to express the maximum
     * @return maximum time recorded by a single event measured by this timer
     */
    double max(TimeUnit unit);

    /**
     * Measures an interval of time from instantiation to an explicit invocation of {@link #stop(io.helidon.metrics.api.Timer)}.
     * <p>
     * A {@code Sample} is not bound to a specific {@code Timer} until it is stopped, at
     * which time the caller specifies which timer to update.
     * </p>
     */
    interface Sample {

        /**
         * Ends the interval, recording the current time as the end time of the interval and applying the elapsed time to the
         * specified {@link io.helidon.metrics.api.Timer}.
         *
         * @param timer the timer to update with this interval
         * @return duration of the sample (in nanoseconds)
         */
        long stop(Timer timer);
    }

    /**
     * Builder for a new {@link io.helidon.metrics.api.Timer}.
     *
     * @see MeterRegistry#getOrCreate(Meter.Builder)
     */
    interface Builder extends Meter.Builder<Builder, Timer> {

        /**
         * Sets the percentiles to compute and publish (expressing, for example, the 95th percentile as 0.95).
         *
         * @param percentiles percentiles to compute and publish
         * @return updated builder
         */
        Builder percentiles(double... percentiles);

        /**
         * Sets the boundary boundaries.
         *
         * @param buckets boundary boundaries
         * @return updated builder
         */
        Builder buckets(Duration... buckets);

        /**
         * Sets the minimum expected value the timer is expected to record.
         *
         * @param min minimum expected value
         * @return updated builder
         */
        Builder minimumExpectedValue(Duration min);

        /**
         * Sets the maximum expected value the timer is expected to record.
         *
         * @param max maximum expected value
         * @return updated builder
         */
        Builder maximumExpectedValue(Duration max);

        /**
         * Prepares the timer to publish a percentile histogram.
         *
         * @param value whether to publish a percentile histogram
         * @return updated builder
         */
        Builder publishPercentileHistogram(boolean value);

        /**
         * Returns the percentiles set in the builder, if any.
         *
         * @return percentiles
         */
        Iterable<Double> percentiles();

        /**
         * Returns the bucket boundary values set in the builder, if any.
         *
         * @return bucket boundary values
         */
        Iterable<Duration> buckets();

        /**
         * Returns the minimum expected value set in the builder, if any.
         *
         * @return minimum expected value if set; empty otherwise
         */
        Optional<Duration> minimumExpectedValue();

        /**
         * Returns the maximum expected value set in the builder, if any.
         *
         * @return maximum expected value if set; empty otherwise
         */
        Optional<Duration> maximumExpectedValue();
    }
}

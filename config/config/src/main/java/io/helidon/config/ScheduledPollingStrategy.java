/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.PollingStrategy;

/**
 * A strategy which allows the user to schedule periodically fired polling event.
 */
public final class ScheduledPollingStrategy implements PollingStrategy {
    private static final Logger LOGGER = Logger.getLogger(ScheduledPollingStrategy.class.getName());

    /*
     * This class will trigger checks in a periodic manner.
     * The actual check if the source has changed is done elsewhere, this is just responsible for telling us
     * "check now".
     * The feedback is an information whether the change happened or not.
     */

    private final RecurringPolicy recurringPolicy;
    private final boolean defaultExecutor;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledFuture;
    private Polled polled;

    private ScheduledPollingStrategy(Builder builder) {
        this.recurringPolicy = builder.recurringPolicy;
        ScheduledExecutorService executor = builder.executor;
        if (executor == null) {
            this.executor = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("file-watch-polling"));
            this.defaultExecutor = true;
        } else {
            this.executor = executor;
            this.defaultExecutor = false;
        }
    }

    /**
     * Creates a polling strategy with an interval of the polling as a parameter.
     * <p>
     * If parameter {@code executor} is {@code null} then a new {@link ScheduledExecutorService} is created.
     *
     * @param recurringPolicy a recurring policy
     * @param executor        an executor
     * @return configured strategy
     */
    public static ScheduledPollingStrategy create(RecurringPolicy recurringPolicy, ScheduledExecutorService executor) {
        return builder()
                .recurringPolicy(recurringPolicy)
                .executor(executor)
                .build();
    }

    /**
     * Fluent API builder for {@link io.helidon.config.ScheduledPollingStrategy}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public synchronized void start(Polled polled) {
        if (defaultExecutor && executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("file-watch-polling"));
        }

        if (executor.isShutdown()) {
            throw new ConfigException("Cannot start a scheduled polling strategy, as the executor service is shutdown");
        }

        this.polled = polled;
        scheduleNext();
    }

    @Override
    public synchronized void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        if (defaultExecutor) {
            ConfigUtils.shutdownExecutor(executor);
        }
    }

    private void scheduleNext() {
        try {
            scheduledFuture = executor.schedule(this::fireEvent,
                                                recurringPolicy.interval().toMillis(),
                                                TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            if (executor.isShutdown()) {
                // intentional shutdown of an executor service
                LOGGER.log(Level.FINEST, "Executor service is shut down, polling is terminated for " + this, e);
            } else {
                // exceptional condition
                LOGGER.log(Level.SEVERE, "Failed to schedule next polling for " + this + ", polling will stop", e);
            }
        }
    }

    private synchronized void fireEvent() {
        ChangeEventType event = polled.poll(Instant.now());
        switch (event) {
        case CHANGED:
        case DELETED:
            recurringPolicy.shorten();
            break;
        case UNCHANGED:
            recurringPolicy.lengthen();
            break;
        case CREATED:
        default:
            break;
        }
        scheduleNext();
    }

    ScheduledExecutorService executor() {
        return executor;
    }

    @Override
    public String toString() {
        return "ScheduledPollingStrategy{"
                + "recurringPolicy=" + recurringPolicy
                + '}';
    }

    /**
     * A fluent API builder for {@link io.helidon.config.ScheduledPollingStrategy}.
     */
    public static final class Builder implements io.helidon.common.Builder<ScheduledPollingStrategy> {
        private RecurringPolicy recurringPolicy;
        private ScheduledExecutorService executor;

        private Builder() {
        }

        @Override
        public ScheduledPollingStrategy build() {
            return new ScheduledPollingStrategy(this);
        }

        /**
         * Configure the recurring policy to use.
         *
         * @param recurringPolicy policy
         * @return updated builder instance
         */
        public Builder recurringPolicy(RecurringPolicy recurringPolicy) {
            this.recurringPolicy = recurringPolicy;
            return this;
        }

        /**
         * Executor service to use to schedule the polling events.
         *
         * @param executor executor service for scheduling events
         * @return updated builder instance
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }
    }

    /**
     * Regular polling strategy implementation.
     *
     * @see io.helidon.config.PollingStrategies#regular(Duration)
     */
    public static class RegularRecurringPolicy implements RecurringPolicy {
        private final Duration interval;

        /**
         * Initialize recurring policy.
         *
         * @param interval regular interval
         */
        public RegularRecurringPolicy(Duration interval) {
            this.interval = interval;
        }

        @Override
        public Duration interval() {
            return interval;
        }

        @Override
        public String toString() {
            return "RegularRecurringPolicy{"
                    + "interval=" + interval
                    + '}';
        }
    }

    static class AdaptiveRecurringPolicy implements RecurringPolicy {

        private final AtomicInteger prolongationFactor = new AtomicInteger(0);

        private final Duration min;
        private final Duration max;
        private final BiFunction<Duration, Integer, Duration> shortenFunction;
        private final BiFunction<Duration, Integer, Duration> lengthenFunction;
        private Duration delay;

        AdaptiveRecurringPolicy(Duration min,
                                Duration initialDelay,
                                Duration max,
                                BiFunction<Duration, Integer, Duration> shortenFunction,
                                BiFunction<Duration, Integer, Duration> lengthenFunction) {
            this.min = min;
            this.max = max;
            this.delay = initialDelay;
            this.shortenFunction = shortenFunction;
            this.lengthenFunction = lengthenFunction;
        }

        @Override
        public Duration interval() {
            return delay;
        }

        @Override
        public void shorten() {
            int factor = prolongationFactor.updateAndGet((i) -> {
                if (i < 0) {
                    --i;
                    return i;
                } else {
                    return -1;
                }
            });
            Duration candidate = shortenFunction.apply(delay, -factor);
            delay = (min.compareTo(candidate) > 0) ? min : candidate;
        }

        @Override
        public void lengthen() {
            int factor = prolongationFactor.updateAndGet((i) -> {
                if (i > 0) {
                    ++i;
                    return i;
                } else {
                    return 1;
                }
            });
            Duration candidate = lengthenFunction.apply(delay, factor);
            delay = (max.compareTo(candidate) > 0) ? candidate : max;
        }

        Duration delay() {
            return delay;
        }
    }

    /**
     * An SPI that allows users to define their own policy how to change the interval between scheduled ticking.
     * <p>
     * The only needed implementation is of {@link #interval()}. Methods {@link #shorten()} and {@link #lengthen()} might be used
     * to shorten or to lengthen an interval. Both of them are called from scheduled polling strategy depending on
     * the result of the polling event ({@link io.helidon.config.spi.PollingStrategy.Polled#poll(java.time.Instant)}.
     */
    @FunctionalInterface
    public interface RecurringPolicy {

        /**
         * Returns the current interval.
         *
         * @return a current interval
         */
        Duration interval();

        /**
         * Allows the {@link RecurringPolicy} to react by shortening the {@link #interval() interval} between ticking.
         * <p>
         * The default implementation is empty.
         */
        default void shorten() {
        }

        /**
         * Allows the {@link RecurringPolicy} to react by prolonging the {@link #interval() interval} between ticking.
         * <p>
         * The default implementation is empty.
         */
        default void lengthen() {
        }

        /**
         * Creates a builder of {@link RecurringPolicy} with an ability to change the behaviour, with a boundaries and
         * the possibility to react to feedback given by {@link #shorten()} or {@link #lengthen()}.
         *
         * @param initialInterval an initial interval
         * @return the new builder
         */
        static AdaptiveBuilder adaptiveBuilder(Duration initialInterval) {
            return new AdaptiveBuilder(initialInterval);
        }

        /**
         * Creates a builder of {@link RecurringPolicy} with an ability to change the behaviour, with a boundaries and
         * the possibility to react to feedback given by {@link #shorten()} or {@link #lengthen()}.
         */
        //* See {@link ScheduledPollingStrategy#adaptive(Duration)} for detailed documentation.
        final class AdaptiveBuilder {

            private final Duration interval;
            private Duration min;
            private Duration max;
            private BiFunction<Duration, Integer, Duration> shortenFunction;
            private BiFunction<Duration, Integer, Duration> lengthenFunction;

            private AdaptiveBuilder(Duration initialInterval) {
                this.interval = initialInterval;
            }

            /**
             * Sets the minimal interval between tick events.
             * <p>
             * The default value is tenth of {@code initialInterval}.
             *
             * @param min a minimal interval
             * @return a modified builder instance
             */
            public AdaptiveBuilder min(Duration min) {
                this.min = min;
                return this;
            }

            /**
             * Sets the maximal interval between tick events.
             * <p>
             * The default value is five times {@code initialInterval}.
             *
             * @param max a minimal interval
             * @return a modified builder instance
             */
            public AdaptiveBuilder max(Duration max) {
                this.max = max;
                return this;
            }

            /**
             * Sets the function that will be used to shorten the interval between ticking.
             * <p>
             *
             * @param shortenFunction a function that shorts the interval
             * @return a modified builder instance
             */
            //See {@link ScheduledPollingStrategy#adaptive(Duration)} to understand the whole picture.
            public AdaptiveBuilder shorten(BiFunction<Duration, Integer, Duration> shortenFunction) {
                this.shortenFunction = shortenFunction;
                return this;
            }

            /**
             * Sets the function that will be used to lengthen the interval between ticking.
             * <p>
             *
             * @param lengthenFunction a function that prolongs the interval
             * @return a modified builder instance
             */
            //See {@link ScheduledPollingStrategy#adaptive(Duration)} to understand the whole picture.
            public AdaptiveBuilder lengthen(BiFunction<Duration, Integer, Duration> lengthenFunction) {
                this.lengthenFunction = lengthenFunction;
                return this;
            }

            /**
             * Builds a new recurring policy.
             *
             * @return the new instance
             */
            public RecurringPolicy build() {
                Duration min = (this.min == null) ? interval.dividedBy(10) : this.min;
                Duration max = (this.max == null) ? interval.multipliedBy(5) : this.max;
                BiFunction<Duration, Integer, Duration> lengthenFunction = (this.lengthenFunction == null)
                        ? DEFAULT_LENGTHEN : this.lengthenFunction;
                BiFunction<Duration, Integer, Duration> shortenFunction = (this.shortenFunction == null)
                        ? DEFAULT_SHORTEN : this.shortenFunction;
                return new ScheduledPollingStrategy.AdaptiveRecurringPolicy(min, interval, max, shortenFunction,
                                                                            lengthenFunction);
            }

            private static final BiFunction<Duration, Integer, Duration> DEFAULT_SHORTEN =
                    (currentDuration, changesFactor) -> currentDuration.dividedBy(2);

            private static final BiFunction<Duration, Integer, Duration> DEFAULT_LENGTHEN =
                    (currentDuration, changesFactor) -> currentDuration.multipliedBy(2);

        }
    }

}

/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.ConfigHelper;
import io.helidon.config.spi.PollingStrategy;

/**
 * A strategy which allows the user to schedule periodically fired a polling event.
 */
public class ScheduledPollingStrategy implements PollingStrategy {

    private static final Logger LOGGER = Logger.getLogger(ScheduledPollingStrategy.class.getName());

    private final RecurringPolicy recurringPolicy;
    private final SubmissionPublisher<PollingStrategy.PollingEvent> ticksSubmitter;
    private final Flow.Publisher<PollingStrategy.PollingEvent> ticksPublisher;

    private final boolean customExecutor;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService executor;

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
        return new ScheduledPollingStrategy(recurringPolicy, executor);
    }

    private ScheduledPollingStrategy(RecurringPolicy recurringPolicy, ScheduledExecutorService executor) {
        Objects.requireNonNull(recurringPolicy, "recurringPolicy cannot be null");

        this.recurringPolicy = recurringPolicy;
        if (executor == null) {
            this.customExecutor = false;
        } else {
            this.customExecutor = true;
            this.executor = executor;
        }

        ticksSubmitter = new SubmissionPublisher<>(Runnable::run, //deliver events on current thread
                                                   1); //(almost) do not buffer events
        ticksPublisher = ConfigHelper.suspendablePublisher(ticksSubmitter,
                                                           this::startScheduling,
                                                           this::stopScheduling);
    }

    @Override
    public Flow.Publisher<PollingEvent> ticks() {
        return ticksPublisher;
    }

    //@Override //TODO WILL BE PUBLIC API AGAIN LATER, Issue #14.
    /*public*/ void configSourceChanged(boolean changed) {
        if (changed) {
            recurringPolicy.shorten();
        } else {
            recurringPolicy.lengthen();
        }
    }

    /**
     * Returns recurring policy.
     *
     * @return recurring policy
     */
    public RecurringPolicy recurringPolicy() {
        return recurringPolicy;
    }

    /*
     * NOTE: TEMPORARILY MOVED FROM POLLING_STRATEGY, WILL BE PUBLIC API AGAIN LATER, Issue #14.
     * <p>
     * Creates a new scheduled polling strategy with an adaptive interval.
     * <p>
     * The minimal interval will not drop bellow {@code initialInterval / 10} and the maximal interval will not exceed {@code
     * initialInterval * 5}.
     * <p>
     * The function that decreases the current interval is defined as a function {@code (currentDuration, changesFactor) ->
     * currentDuration.dividedBy(2)} (half the current), whilst the function that increases the current interval is
     * defined as {@code (currentDuration, changesFactor) -> currentDuration.multiplyBy(2)} (doubled the current),
     * where {@code currentDuration} is the currently valid duration and {@code
     * changesFactor} is a number of consecutive reloading configuration that brings the change or not ({@link
     * RecurringPolicy#lengthen()} or {@link RecurringPolicy#shorten()} is called by {@link PollingStrategy} from {@link
     * ScheduledPollingStrategy#configSourceChanged(boolean)}). The {@code changeFactor} might be positive (changes proceeded) or
     * negative (changes did not proceed). In other words, more consecutive reloads with a change mean higher {@code changeFactor}
     * and more consecutive reloads without any change mean lower {@code changeFactor}. When {@code changeFactor} is
     * negative and on last fired event change has proceeded then {@code changeFactor} will be {@code +1}, and,
     * conversely, positive number, regardless how high, is changed to {@code -1} when load was fired for nothing.
     * Note that the default implementations do not take {@code changeFactor} into account and {@link
     * RecurringPolicy.AdaptiveBuilder#shortenFunction} just halves the current interval and {@link
     * RecurringPolicy.AdaptiveBuilder#lengthenFunction} doubles it.
     * <p>
     * If you need to adjust some of the parameters of the adaptive recurring policy, try {@link
     * RecurringPolicy#adaptiveBuilder(Duration)} or make your own {@link RecurringPolicy} and create the  scheduled polling
     * strategy by calling {@link #recurringPolicyBuilder(RecurringPolicy)}.
     *
     * @param initialInterval an initial interval
     * @return a polling strategy
     * @see RecurringPolicy
     */
    /*
    static PollingStrategy adaptive(Duration initialInterval) {
        return new PollingStrategies.ScheduledBuilder(RecurringPolicy.adaptiveBuilder(initialInterval).build()).build();
    }
    */

    /*
     * NOTE: TEMPORARILY MOVED FROM POLLING_STRATEGY, WILL BE PUBLIC API AGAIN LATER, Issue #14.
     * <p>
     * Creates a scheduling polling strategy builder which allows users to add their own scheduler executor service.
     *
     * @param recurringPolicy a recurring policy
     * @return a new builder
     */
    /*
    static PollingStrategies.ScheduledBuilder recurringPolicyBuilder(RecurringPolicy recurringPolicy) {
        return new PollingStrategies.ScheduledBuilder(recurringPolicy);
    }
    */
    synchronized void startScheduling() {
        if (!customExecutor) {
            this.executor = Executors.newScheduledThreadPool(1, new ConfigThreadFactory("scheduled-polling"));
        }
        scheduleNext();
    }

    private void scheduleNext() {
        scheduledFuture = executor.schedule(this::fireEvent,
                                            recurringPolicy.interval().toMillis(),
                                            TimeUnit.MILLISECONDS);
    }

    private void fireEvent() {
        ticksSubmitter.offer(
                PollingEvent.now(),
                (subscriber, pollingEvent) -> {
                    LOGGER.log(Level.FINER, String.format("Event %s has not been delivered to %s.", pollingEvent, subscriber));
                    return false;
                });
        scheduleNext();
    }

    synchronized void stopScheduling() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        if (!customExecutor) {
            ConfigUtils.shutdownExecutor(executor);
            executor = null;
        }
    }

    ScheduledFuture<?> scheduledFuture() {
        return scheduledFuture;
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

        private final Duration min;
        private final Duration max;
        private final BiFunction<Duration, Integer, Duration> shortenFunction;
        private final BiFunction<Duration, Integer, Duration> lengthenFunction;
        private Duration delay;

        private AtomicInteger prolongationFactor = new AtomicInteger(0);

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
                    return --i;
                } else {
                    return -1;
                }
            });
            Duration candidate = shortenFunction.apply(delay, -factor);
            delay = min.compareTo(candidate) > 0 ? min : candidate;
        }

        @Override
        public void lengthen() {
            int factor = prolongationFactor.updateAndGet((i) -> {
                if (i > 0) {
                    return ++i;
                } else {
                    return 1;
                }
            });
            Duration candidate = lengthenFunction.apply(delay, factor);
            delay = max.compareTo(candidate) > 0 ? candidate : max;
        }

        Duration delay() {
            return delay;
        }
    }

    /**
     * NOTE: TEMPORARILY MOVED FROM POLLING_STRATEGY, WILL BE PUBLIC API AGAIN LATER, Issue #14.
     * <p>
     * An SPI that allows users to define their own policy how to change the interval between scheduled ticking.
     * <p>
     * The only needed implementation is of {@link #interval()}. Methods {@link #shorten()} and {@link #lengthen()} might be used
     * to shorten or to lengthen an interval. Both of them are called from scheduled polling strategy {@link
     * ScheduledPollingStrategy#configSourceChanged(boolean) method}.
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
         * <p>
         */
        //* See {@link ScheduledPollingStrategy#adaptive(Duration)} for detailed documentation.
        final class AdaptiveBuilder {

            private Duration interval;
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
                Duration min = this.min == null ? interval.dividedBy(10) : this.min;
                Duration max = this.max == null ? interval.multipliedBy(5) : this.max;
                BiFunction<Duration, Integer, Duration> lengthenFunction = this.lengthenFunction == null
                        ? DEFAULT_LENGTHEN : this.lengthenFunction;
                BiFunction<Duration, Integer, Duration> shortenFunction = this.shortenFunction == null
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

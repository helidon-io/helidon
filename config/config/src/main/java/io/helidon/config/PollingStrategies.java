/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import io.helidon.common.reactive.Flow;
import io.helidon.config.internal.FilesystemWatchPollingStrategy;
import io.helidon.config.internal.ScheduledPollingStrategy;
import io.helidon.config.internal.ScheduledPollingStrategy.RegularRecurringPolicy;
import io.helidon.config.spi.PollingStrategy;

/**
 * Built-in {@link io.helidon.config.spi.PollingStrategy} implementations.
 * <p>
 * The static factory methods offer convenient ways to obtain commonly-used
 * {@link PollingStrategy} implementations.
 *
 * @see #regular(java.time.Duration)
 * @see #watch(java.nio.file.Path)
 * @see #nop()
 * @see io.helidon.config.spi.PollingStrategy
 */
public final class PollingStrategies {

    private PollingStrategies() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Provides a default polling strategy that does not fire an event at all.
     *
     * @return no-operation polling strategy instance
     */
    public static PollingStrategy nop() {
        return NopPollingStrategyHolder.NOP;
    }

    /**
     * Provides a scheduled polling strategy with a specified constant interval.
     *
     * @param interval an interval between polling
     * @return a regular polling strategy
     */
    public static ScheduledBuilder regular(Duration interval) {
        return new ScheduledBuilder(new RegularRecurringPolicy(interval));
    }

    /**
     * Provides a filesystem watch polling strategy with a specified watched path.
     *
     * @param watchedPath a path which should be watched
     * @return a filesystem watching polling strategy
     */
    public static FilesystemWatchBuilder watch(Path watchedPath) {
        return new FilesystemWatchBuilder(watchedPath);
    }

    /**
     * A builder for a scheduled polling strategy.
     */
    public static final class ScheduledBuilder implements Supplier<PollingStrategy> {

        private static final String INTERVAL_KEY = "interval";

        private ScheduledPollingStrategy.RecurringPolicy recurringPolicy;
        private ScheduledExecutorService executor;

        /*private*/ ScheduledBuilder(ScheduledPollingStrategy.RecurringPolicy recurringPolicy) {
            this.recurringPolicy = recurringPolicy;
        }

        /**
         * Initializes polling strategy instance from configuration properties.
         * <p>
         * Mandatory {@code properties}, see {@link PollingStrategies#regular(Duration)}:
         * <ul>
         * <li>{@code interval} - type {@link Duration}, e.g. {@code PT15S} means 15 seconds</li>
         * </ul>
         *
         * @param metaConfig meta-configuration used to initialize returned polling strategy builder instance from.
         * @return new instance of polling strategy builder described by {@code metaConfig}
         * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
         *                                required by the mapper implementation to provide instance of Java type.
         * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
         *                                supplied configuration node to an instance of a given Java type.
         * @see PollingStrategies#regular(Duration)
         */
        public static ScheduledBuilder from(Config metaConfig) throws ConfigMappingException, MissingValueException {
            return PollingStrategies.regular(metaConfig.get(INTERVAL_KEY).as(Duration.class));
        }

        /**
         * Sets a custom {@link ScheduledExecutorService service} used to schedule polling ticks on.
         * <p>
         * By default it is a new thread pool executor per polling strategy instance.
         *
         * @param executor the custom scheduled executor service
         * @return a modified builder instance
         */
        public ScheduledBuilder executor(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds a new polling strategy.
         *
         * @return the new instance
         */
        public PollingStrategy build() {
            ScheduledExecutorService executor = this.executor;
            return new ScheduledPollingStrategy(recurringPolicy, executor);
        }

        @Override
        public PollingStrategy get() {
            return build();
        }
    }

    /**
     * A builder for a filesystem watch polling strategy.
     */
    public static final class FilesystemWatchBuilder implements Supplier<PollingStrategy> {

        private Path path;
        private ScheduledExecutorService executor;

        /*private*/ FilesystemWatchBuilder(Path path) {
            this.path = path;
        }

        /**
         * Sets a custom {@link ScheduledExecutorService executor} used to watch filesystem changes on.
         * <p>
         * By default single-threaded executor is used.
         *
         * @param executor the custom scheduled executor service
         * @return a modified builder instance
         */
        public FilesystemWatchBuilder executor(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds a new polling strategy.
         *
         * @return the new instance
         */
        public PollingStrategy build() {
            return new FilesystemWatchPollingStrategy(path, executor);
        }

        @Override
        public PollingStrategy get() {
            return build();
        }
    }

    /**
     * Holder of singleton instance of NOP implementation of {@link PollingStrategy}.
     * Returned strategy does not fire an event at all.
     */
    private static final class NopPollingStrategyHolder {

        private NopPollingStrategyHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        /**
         * NOP singleton instance.
         */
        private static final PollingStrategy NOP = () -> Flow.Subscriber::onComplete;
    }

}

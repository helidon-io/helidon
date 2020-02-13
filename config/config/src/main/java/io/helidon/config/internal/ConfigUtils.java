/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Priority;

import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode;

/**
 * Internal config utilities.
 */
public final class ConfigUtils {

    private static final Logger LOGGER = Logger.getLogger(ConfigUtils.class.getName());

    private ConfigUtils() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Convert iterable items to an ordered serial stream.
     *
     * @param items items to be streamed.
     * @param <S>   expected streamed item type.
     * @return stream of items.
     */
    public static <S> Stream<S> asStream(Iterable<? extends S> items) {
        return asStream(items.iterator());
    }

    /**
     * Converts an iterator to a stream.
     *
     * @param <S> type of the base items
     * @param iterator iterator over the items
     * @return stream of the items
     */
    public static <S> Stream<S> asStream(Iterator<? extends S> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    /**
     * Sorts items represented by an {@link Iterable} instance based on a {@code Priority} annotation attached to each
     * item's Java type and return the sorted items as an ordered serial {@link Stream}.
     * <p>
     * Instances are sorted by {@code Priority.value()} attached <em>directly</em> to the instance of each item's class.
     * If there is no {@code Priority} annotation attached to an item's class the {@code defaultPriority} value is used
     * instead. Items with higher priority values have higher priority and take precedence (are returned sooner from
     * the stream).
     *
     * @param items           items to be ordered by priority and streamed.
     * @param defaultPriority default priority to be used in case an item does not have a priority defined.
     * @param <S>             item type.
     * @return prioritized stream of items.
     */
    public static <S> Stream<? extends S> asPrioritizedStream(Iterable<? extends S> items, int defaultPriority) {
        return asStream(items).sorted(priorityComparator(defaultPriority));
    }

    /**
     * Returns a comparator for two objects, the classes for which are
     * optionally annotated with {@link Priority} and which applies a specified
     * default priority if either or both classes lack the annotation.
     *
     * @param <S> type of object being compared
     * @param defaultPriority used if the classes for either or both objects
     * lack the {@code Priority} annotation
     * @return comparator
     */
    public static <S> Comparator<S> priorityComparator(int defaultPriority) {
        return (service1, service2) -> {
            int service1Priority = Optional.ofNullable(service1.getClass().getAnnotation(Priority.class))
                    .map(Priority::value)
                    .orElse(defaultPriority);
            int service2Priority = Optional.ofNullable(service2.getClass().getAnnotation(Priority.class))
                    .map(Priority::value)
                    .orElse(defaultPriority);
            return service2Priority - service1Priority;
        };
    }

    /**
     * Builds map into object node.
     * <p>
     * Dots in keys are interpreted as tree-structure separators.
     *
     * @param map    source map
     * @param strict In strict mode, properties overlapping causes failure during loading into internal structure.
     * @return built object node from map source.
     */
    public static ConfigNode.ObjectNode mapToObjectNode(Map<String, String> map, boolean strict) {
        ConfigNode.ObjectNode.Builder builder = ConfigNode.ObjectNode.builder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            try {
                builder.addValue(entry.getKey(), entry.getValue());
            } catch (ConfigException ex) {
                if (strict) {
                    throw ex;
                } else {
                    LOGGER.log(Level.CONFIG, "Tree-structure failure on key '" + entry.getKey() + "', reason: "
                            + ex.getLocalizedMessage());
                    LOGGER.log(Level.FINEST, "Detailed reason of failure of adding key '" + entry.getKey()
                            + "' = '" + entry.getValue() + "'.", ex);
                }
            }
        }
        return builder.build();
    }

    /**
     * Transforms {@link java.util.Properties} to {@code Map<String, String>}.
     * <p>
     * It iterates just {@link Properties#stringPropertyNames() string property names} and uses it's
     * {@link Properties#getProperty(String) string value}.
     *
     * @param properties properties to be transformed to map
     * @return transformed map
     */
    public static Map<String, String> propertiesToMap(Properties properties) {
        return properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(k -> k, properties::getProperty));
    }

    /**
     * Shutdowns {@code executor} and waits for it.
     *
     * @param executor executor to be shutdown.
     */
    public static void shutdownExecutor(ScheduledExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    /**
     * Returns a {@link Charset} instance parsed from specified {@code content-encoding} HTTP response header
     * or {@code UTF-8} if the header is missing.
     *
     * @param contentEncoding {@code content-type} HTTP response header
     * @return {@link Charset} parsed from {@code contentEncoding}
     * or {@code UTF-8} in case a {@code contentEncoding} is {@code null}
     * @throws ConfigException in case of unsupported charset name
     */
    public static Charset getContentCharset(String contentEncoding) throws ConfigException {
        try {
            return Optional.ofNullable(contentEncoding)
                    .map(Charset::forName)
                    .orElse(StandardCharsets.UTF_8);
        } catch (UnsupportedCharsetException ex) {
            throw new ConfigException("Unsupported response content-encoding '" + contentEncoding + "'.", ex);
        }
    }

    /**
     * Allows to {@link #schedule()} execution of specified {@code command} using specified {@link ScheduledExecutorService}.
     * Task is not executed immediately but scheduled with specified {@code delay}.
     * It is possible to postpone an execution of the command by calling {@link #schedule()} again before the command is finished.
     * <p>
     * It can be used to implement Rx Debounce operator (http://reactivex.io/documentation/operators/debounce.html).
     */
    public static class ScheduledTask {
        private final ScheduledExecutorService executorService;
        private final Runnable command;
        private final Duration delay;
        private volatile ScheduledFuture<?> scheduled;
        private final Object lock = new Object();

        /**
         * Initialize task.
         *
         * @param executorService service to be used to schedule {@code command} execution on
         * @param command         the command to be executed on {@code executorService}
         * @param delay           the {@code command} is scheduled with specified delay
         */
        public ScheduledTask(ScheduledExecutorService executorService, Runnable command, Duration delay) {
            this.executorService = executorService;
            this.command = command;
            this.delay = delay;
        }

        /**
         * Schedule execution of {@code command} on specified {@code executorService} with initial {@code delay}.
         * <p>
         * Scheduling can be repeated. Not finished task is canceled.
         *
         * @return whether a previously-scheduled action was canceled in scheduling this new new action
         */
        public boolean schedule() {
            boolean result = false;
            synchronized (lock) {
                if (scheduled != null) {
                    if (!scheduled.isCancelled() && !scheduled.isDone()) {
                        scheduled.cancel(false);
                        LOGGER.log(Level.FINER, String.format("Cancelling and rescheduling %s task.", command));
                        result = true;
                    }
                }
                scheduled = executorService.schedule(command, delay.toMillis(), TimeUnit.MILLISECONDS);
            }
            return result;
        }
    }

    /**
     * Holder of singleton instance of {@link ConfigNode.ObjectNode}.
     *
     * @see ConfigNode.ObjectNode#empty()
     */
    public static final class EmptyObjectNodeHolder {

        private EmptyObjectNodeHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        /**
         * EMPTY singleton instance.
         */
        public static final ConfigNode.ObjectNode EMPTY = ConfigNode.ObjectNode.builder().build();
    }

}

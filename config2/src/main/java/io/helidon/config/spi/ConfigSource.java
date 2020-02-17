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

package io.helidon.config.spi;

import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigNode;
import io.helidon.config.Key;

/**
 * Do not implement this interface directly, rather choose one or more of the interfaces in this class.
 * <p>
 * Interfaces that can be implemented, from the point of "eagerness" of loading of data:
 * <ul>
 *     <li>{@link io.helidon.config.spi.ConfigSource.EagerSource} - a source that loads configuration tree from the
 *     underlying origin in one go</li>
 *     <li>{@link io.helidon.config.spi.ConfigSource.LazySource} - a source that reads specific keys only (such as when the
 *     underlying origin is too big, or does not support querying of all keys</li>
 * </ul>
 * Interfaces that can be implemented, from the point of support for mutability (default is immutable):
 * <ul>
 *     <li>{@link io.helidon.config.spi.ConfigSource.EventSource} - a source capable of triggering events on change of underlying
 *     origin.</li>
 *     <li>{@link io.helidon.config.spi.ConfigSource.WatchableSource} - a source that provides a target of the origin data
 *      that can be used by a polling strategy (such as {@link java.nio.file.Path}</li>
 *     <li>{@link io.helidon.config.spi.ConfigSource.PollableSource} - a source that provides a stamp
 *     of the underlying origin data that can be used to determine changes in the origin data (such as digest of a file)</li>
 * </ul>
 * {@link io.helidon.config.spi.ConfigSource.EagerSource} sources in addition support two types of providing configuration tree:
 * <ul>
 *     <li>Parsable content - the config source provides an array of bytes, a parser is used to process it based on configuration
 *      or media type of the content</li>
 *     <li>{@link io.helidon.config.ConfigNode} - the actual configuration tree of the source</li>
 * </ul>
 */
public interface ConfigSource {
    /**
     * Short, human-readable summary referring to the underlying source.
     * <p>
     * For example, a file path or a URL or any other information that helps the
     * user recognize the underlying origin of the data this {@code Source}
     * provides.
     * <p>
     * Default is the implementation class simple name with any {@code "Source"}
     * suffix removed.
     *
     * @return description of the source
     */
    default String description() {
        String name = this.getClass().getSimpleName();
        if (name.endsWith("Source")) {
            name = name.substring(0, name.length() - "Source".length());
        }
        return name;
    }

    /**
     * Whether the underlying config data exists.
     *
     * @return {@code true} if there is data to be read, {@code false} otherwise
     */
    default boolean exists() {
        return true;
    }

    default Optional<RetryPolicy> retryPolicy() {
        return Optional.empty();
    }

    default boolean optional() {
        return false;
    }

    interface Builder<T extends Builder<T>> extends io.helidon.common.Builder<ConfigSource> {
        T retryPolicy(RetryPolicy policy);

        T optional(boolean optional);
    }

    /**
     * A source that can read all data from the underlying origin as a stream that can be parsed based on its media type.
     */
    @FunctionalInterface
    interface ParsableSource extends ConfigSource {
        /**
         * Loads the underlying source data. This method is only called when the source {@link #exists()}.
         * <p>
         * The method can be invoked repeatedly, for example during retries.
         *
         * @return An instance of {@code T} as read from the underlying origin of the data (if it exists)
         * @throws io.helidon.config.ConfigException in case of errors loading from the underlying origin
         */
        Content.ParsableContent load() throws ConfigException;

        default Optional<ConfigParser> parser() {
            return Optional.empty();
        }

        default Optional<String> mediaType() {
            return Optional.empty();
        }

        /**
         * Closes the @{code Source}, releasing any resources it holds.
         */
        default void close() {
        }

        interface Builder<T extends Builder<T>> extends ConfigSource.Builder<T> {
            T parser(ConfigParser parser);

            T mediaType(String mediaType);
        }
    }

    /**
     * A source that can read all data from the underlying origin as a configuration node.
     */
    @FunctionalInterface
    interface NodeSource extends ConfigSource {
        /**
         * Loads the underlying source data. This method is only called when the source {@link #exists()}.
         * <p>
         * The method can be invoked repeatedly, for example during retries.
         *
         * @return An instance of {@code T} as read from the underlying origin of the data (if it exists)
         * @throws io.helidon.config.ConfigException in case of errors loading from the underlying origin
         */
        Content.NodeContent load() throws ConfigException;

        /**
         * Closes the @{code Source}, releasing any resources it holds.
         */
        default void close() {
        }
    }


    /**
     * A source that is not capable of loading all keys at once.
     */
    @FunctionalInterface
    interface LazySource extends ConfigSource {
        Optional<ConfigNode> node(String key);
    }

    /**
     * A source that supports modifications.
     * A mutable source may either support a change subscription mechanism (e.g. the source is capable of notifications that
     * do not require external polling), or an explicit polling strategy that provides handling of change notifications
     * as supported by {@link io.helidon.config.spi.ConfigSource.PollableSource}.
     *
     * Examples:
     *  - a cloud service may provide notifications through its API that a node changed
     *  - a file source can be watched using a file watch polling strategy
     *  - a file source can be watched using a time based polling strategy
     */
    interface EventSource {
        /**
         * Register a change listener.
         *
         * @param changedNode the key and node of the configuration that changed. This may be the whole config tree, or a specific
         *                    node depending on how fine grained the detection mechanism is
         */
        void onChange(BiConsumer<Key, ConfigNode> changedNode);
    }

    /**
     * Mark a config source with this interface to announce that this source provides a target that a Polling strategy can
     * use.
     * The actual target is provided when invoking
     * {@link Content.Builder#pollingTarget(Object)}.
     *
     * @param <T> Type of target of this config source
     * @see io.helidon.config.spi.ChangeWatcher
     */
    interface WatchableSource<T> {
        Class<T> targetType();

        default Optional<ChangeWatcher<?>> changeWatcher() {
            return Optional.empty();
        }

        interface Builder<B extends Builder<B, T>, T> extends ConfigSource.Builder<B> {
            B changeWatcher(ChangeWatcher<T> changeWatcher);
        }
    }

    /**
     * A config source that supports usage of polling strategies that do regular checks.
     *
     * @param <S> type of content stamp
     */
    interface PollableSource<S> {
        /**
         * This method is invoked to check if this source has changed.
         * @param stamp the stamp of the last loaded content
         * @return {@code true} if the current data of this config source differ from the loaded data
         */
        boolean isModified(S stamp);

        default Optional<PollingStrategy> pollingStrategy() {
            return Optional.empty();
        }

        /**
         * As there is no multi-inheritance in java, this must be an interface.
         * You can use our abstract config source builder implementation as a base for your class.
         */
        interface Builder<T extends Builder<T>> extends ConfigSource.Builder<T> {
            T pollingStrategy(PollingStrategy pollingStrategy);
        }
    }
}

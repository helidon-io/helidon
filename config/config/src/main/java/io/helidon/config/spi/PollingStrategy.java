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

package io.helidon.config.spi;

import java.time.Duration;
import java.time.Instant;

import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;

/**
 * Mechanism for notifying interested listeners when they should check for
 * changes that might have been made to the data used to create a {@code Config}
 * tree, as accessed through {@link io.helidon.config.spi.PollableSource}s.
 * <p>
 * Once it loads a {@link Config} tree from {@link ConfigSource}s the config
 * system does not itself change the in-memory {@code Config} tree. Even so, the
 * underlying data available via the tree's {@code ConfigSource}s can change.
 * The Config system nevertheless supports change notification through
 * {@link Config#onChange(java.util.function.Consumer)} and that is enabled
 * also by polling strategies.
 * <p>
 * In implementations of {@code PollingStrategy} provide a notification mechanism
 * through {@link #start(io.helidon.config.spi.PollingStrategy.Polled)}, where the
 * polled component receives events that should check for changes.
 * In config system itself, this is handled by internals and is not exposed outside
 * of it.
 * <p>
 * A config source implements appropriate functionality in method
 * {@link io.helidon.config.spi.PollableSource#isModified(Object)}, which will
 * be invoked each time a polling strategy triggers the listener.
 * <p>
 * As described with {@link io.helidon.config.MetaConfig#configSource(io.helidon.config.Config)}, the config system can
 * load {@code ConfigSource}s using meta-configuration, which supports
 * specifying polling strategies. All {@link PollingStrategies built-in polling
 * strategies} and custom ones are supported.
 * See {@link io.helidon.config.spi.PollingStrategyProvider} for details.
 * <p>
 * The meta-configuration for a config source can set the property
 * {@code polling-strategy} using the following nested {@code properties}:
 * <ul>
 * <li>{@code type} - name of the polling strategy implementation (referencing the Java Service Loader service)
 * <table class="config">
 * <caption>Built-in Polling Strategies</caption>
 *  <tr>
 *      <th>Name</th>
 *      <th>Strategy</th>
 *      <th>Required Properties</th>
 *  </tr>
 *  <tr>
 *      <td>{@code regular}</td>
 *      <td>Scheduled polling at regular intervals. See
 *      {@link PollingStrategies#regular(Duration)}.</td>
 *      <td>{@code interval} in {@link Duration} format, e.g. {@code PT15S} means 15
 *      seconds</td>
 *  </tr>
 * </table>
 * <p>
 * </li>
 * <li>{@code class} - fully-qualified class name of a custom polling strategy
 * implementation or a builder class that implements a {@code build()} method
 * that returns a {@code PollingStrategy}.
 * </li>
 * </ul>
 * For a given config source use either {@code type} or {@code class} to
 * indicate a polling strategy but not both. If both appear the config system
 * ignores the {@code class} setting.
 * <h3>Meta-configuration Support for Custom Polling Strategies</h3>
 * To support settings in meta-configuration, a custom polling strategy must
 * be capable of processing the meta configuration provided to
 * {@link io.helidon.config.spi.PollingStrategyProvider#create(String, io.helidon.config.Config)}
 *
 * @see PollingStrategies PollingStrategies - access built-in implementations.
 * @see io.helidon.config.spi.PollingStrategyProvider to implement custom polling strategies
 * @see io.helidon.config.spi.ChangeWatcher to implement change watchers that notify config system when a target actually changes
 */
@FunctionalInterface
public interface PollingStrategy {

    /**
     * Start this polling strategy. From this point in time, the polled will receive
     *  events on {@link Polled#poll(java.time.Instant)}.
     * It is the responsibility of the {@link io.helidon.config.spi.PollingStrategy.Polled}
     * to handle such requests.
     * A {@link io.helidon.config.spi.ConfigSource} needs only support for polling stamps
     * to support a polling strategy, the actual reloading is handled by the
     * configuration component.
     * There is no need to implement {@link io.helidon.config.spi.PollingStrategy.Polled} yourself,
     * unless you want to implement a new component that supports polling.
     * Possible reloads of configuration are happening within the thread that invokes this method.
     *
     * @param polled a component receiving polling events.
     */
    void start(Polled polled);

    /**
     * Stop polling and release all resources.
     */
    default void stop() {
    }

    /**
     * A polled component. For config this interface is implemented by the config system itself.
     */
    @FunctionalInterface
    interface Polled {
        /**
         * Poll for changes.
         * The result may be used to modify behavior of the {@link io.helidon.config.spi.PollingStrategy} triggering this
         * poll event.
         *
         * @param when instant this polling request was created
         * @return result of the polling
         */
        ChangeEventType poll(Instant when);
    }

}

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

package io.helidon.config.spi;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import io.helidon.common.reactive.Flow;
import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;

/**
 * Mechanism for notifying interested subscribers when they should check for
 * changes that might have been made to the data used to create a {@code Config}
 * tree, as accessed through {@link ConfigSource}s.
 * <p>
 * Once it loads a {@link Config} tree from {@link ConfigSource}s the config
 * system does not itself change the in-memory {@code Config} tree. Even so, the
 * underlying data available via the tree's {@code ConfigSource}s can change.
 * Implementations of {@code PollingStrategy} other interested code to learn
 * when changes to that underlying data might have occurred.
 * <p>
 * In implementations of {@code PollingStrategy} the {@link #ticks()} method
 * returns a {@link Flow.Publisher} of {@link PollingEvent}s to which the
 * application or the {@code ConfigSource}s themselves can subscribe. Generally,
 * each event is a hint to the application or a {@code ConfigSource} itself that
 * it should check to see if any of the underlying config data it relies on
 * might have changed. Note that a {@code PollingStrategy}'s publication of an
 * event does not necessarily guarantee that the underlying data has in fact
 * changed, although this might be true for some {@code PollingStrategy}
 * implementations.
 * <p>
 * Typically a custom {@link ConfigSource} implementation creates a
 * {@code Flow.Subscriber} which it uses to subscribe to the
 * {@code Flow.Publisher} that is returned from the
 * {@code PollingStrategy.ticks()} method. When that subscriber receives a
 * {@code PollingEvent} it triggers the {@code ConfigSource} to reload the
 * configuration from the possibly changed underlying data. For example, each
 * {@link AbstractParsableConfigSource} can use a different
 * {@code PollingStrategy}.
 * <p>
 * As described with {@link ConfigSource#create(Config)}, the config system can
 * load {@code ConfigSource}s using meta-configuration, which supports
 * specifying polling strategies. All {@link PollingStrategies built-in polling
 * strategies} and custom ones are supported. (The support is tightly connected
 * with {@link AbstractSource.Builder#init(Config) AbstractSource extensions}
 * and will not be automatically provided by any another config source
 * implementations.)
 * <p>
 * The meta-configuration for a config source can set the property
 * {@code polling-strategy} using the following nested {@code properties}:
 * <ul>
 * <li>{@code type} - name of the polling strategy implementation.
 * <table class="config">
 * <caption>Built-in Polling Strategies</caption>
 * <tr>
 * <th>Name</th>
 * <th>Strategy</th>
 * <th>Required Properties</th>
 * </tr>
 * <tr>
 * <td>{@code regular}</td>
 * <td>Scheduled polling at regular intervals. See
 * {@link PollingStrategies#regular(Duration)}.</td>
 * <td>{@code interval} in {@link Duration} format, e.g. {@code PT15S} means 15
 * seconds</td>
 * </tr>
 * <tr>
 * <td>{@code watch}</td>
 * <td>Filesystem monitoring of the {@code Path} specified in the config source
 * definition. See {@link PollingStrategies#watch(Path)}.
 * <p>
 * Use this strategy only with config sources based on
 * {@link AbstractSource.Builder} that are paramaterized with {@code Path}. This
 * includes null {@link io.helidon.config.ConfigSources#classpath(String) classpath},
 * {@link io.helidon.config.ConfigSources#file(String) file} and
 * {@link io.helidon.config.ConfigSources#directory(String) directory} config
 * sources.
 * </td>
 * <td>n/a</td>
 * </tr>
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
 * follow these patterns.
 * <ol>
 * <li>Auto-configuration from meta-configuration properties
 * <p>
 * The implementation class should define a Java bean property for each
 * meta-configuration property it needs to support. The config system uses
 * mapping functions to convert the text in the
 * meta-configuration into the correct Java type and then assigns the value to
 * the correspondingly-named Java bean property defined on the custom strategy
 * instance. See the built-in mappers defined in
 * {@link io.helidon.config.ConfigMappers} to see what Java types are automatically
 * supported.
 * </li>
 * <li>Accessing the {@code ConfigSource} meta-config attributes
 * <p>
 * The custom polling strategy can get access to the same meta-configuration
 * attributes that are used to construct the associated {@code ConfigSource}. To
 * do so the custom implementation class should implement a constructor that
 * accepts the same Java type as that returned by the
 * {@link AbstractSource.Builder#target()} method on the builder that is used
 * to construct the {@code ConfigSource}.
 * <p>
 * For example, a custom polling strategy useful with {@code ConfigSource}s
 * based on a {@code Path} would implement a constructor that accepts a
 * {@code Path} argument.
 * </li>
 * </ol>
 *
 * @see AbstractParsableConfigSource.Builder#pollingStrategy(Supplier)
 * @see Flow.Publisher
 * @see PollingStrategies PollingStrategies - access built-in implementations.
 */
public interface PollingStrategy extends Supplier<PollingStrategy> {

    @Override
    default PollingStrategy get() {
        return this;
    }

    /**
     * Returns a {@link Flow.Publisher} which fires {@link PollingEvent}s.
     * <p>
     * Note that {@code PollingStrategy} implementations can generate
     * {@code PollingEvent}s whether or not any subscribers have subscribed to
     * the publisher of the events.
     * <p>
     * Subscribers typically invoke {@link Flow.Subscription#request(long)}
     * asking for one event initially, and then after it has processed each
     * event the subscriber requests one more event.
     * <p>
     * The subscriber might not receive every event broadcast, for example if it
     * subscribes to the publisher after an event has been delivered to the
     * publisher.
     * <p>
     * Each {@code PollingStrategy} implementation chooses which executor to use
     * for notifying subscribers. The recommended practice is to use the same
     * thread as the polling strategy implementation runs on.
     *
     * @return a publisher of events
     */
    Flow.Publisher<PollingEvent> ticks();

//    /**
//     * Notifies a polling strategy that a configuration source has been changed since a precedent {@link PollingEvent} had been
//     * fired.
//     * <p>
//     * The default implementation does not do anything, but can be overridden to change a behaviour of the polling strategy, for
//     * example, to change delay between ticking or just to log it.
//     *
//     * @param changed {@code true} if source was changed since a precedent {@link PollingEvent}
//     * @see RecurringPolicy
//     * @see RecurringPolicy#shorten()
//     * @see RecurringPolicy#lengthen()
//     */
    //default void configSourceChanged(boolean changed) { //TODO WILL BE PUBLIC API AGAIN LATER, Issue #14.
    //}

    /**
     * Event indicating that data used in constructing a given {@code Config}
     * tree might have changed.
     *
     * @see PollingStrategy#ticks()
     */
    interface PollingEvent {

        /**
         * Returns the event timestamp.
         *
         * @return event timestamp
         */
        Instant timestamp();

        /**
         * Creates a new instance of {@link PollingEvent} with
         * {@link Instant#now()} used as its timestamp.
         *
         * @return new instance of event
         */
        static PollingEvent now() {
            Instant timestamp = Instant.now();
            return new PollingEvent() {
                @Override
                public Instant timestamp() {
                    return timestamp;
                }

                @Override
                public String toString() {
                    return "PollingEvent @ " + timestamp;
                }
            };
        }
    }

}

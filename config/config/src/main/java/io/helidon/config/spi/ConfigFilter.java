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

package io.helidon.config.spi;

import io.helidon.config.Config;

/**
 * Filter that can transform elementary configuration ({@code String}) values
 * before they are returned via the {@link Config} API.
 * <p>
 * The application can register filters with a builder by invoking
 * {@link Config.Builder#addFilter(ConfigFilter)} or {@link
 * Config.Builder#addFilter(java.util.function.Function)}. The
 * config system also locates filters using the Java
 * {@link java.util.ServiceLoader} mechanism and automatically adds them to
 * every {@code Builder} unless the application disables this feature for a
 * given {@code Builder} by invoking
 * {@link Config.Builder#disableFilterServices()}.
 * <p>
 * A filter can specify a {@link javax.annotation.Priority}. If no priority is
 * explicitly assigned, the value of {@value PRIORITY} is assumed.
 * <h2>Initializing Filters</h2>
 * Any filter that uses the {@code Config} instance during its initialization
 * should do so in its {@link #init(Config)} method,
 * <em>not</em>
 * in its constructor. The {@code Config.Builder.build()} method invokes each
 * filter's `init` method according to the filters' priority order and just
 * before returning the new {@code Config} instance to the application.
 * <p>
 * If a filter's {@code init} method uses {@code Config#get} to retrieve config
 * information, then -- as always -- the config system will invoke the
 * {@code apply} method on every filter which the application added to the
 * builder. But the {@code init} methods of filters with lower priority than the
 * current filter <em>will not</em> have executed. Developers should keep this
 * in mind while writing filter {@code init} methods.
 *
 * @see Config.Builder#addFilter(ConfigFilter)
 * @see Config.Builder#addFilter(java.util.function.Function)
 */
@FunctionalInterface
public interface ConfigFilter {

    /**
     * Default priority of the filter if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    int PRIORITY = 100;

    /**
     * Filters an elementary config value before it is made available to the
     * application via the {@code Config} API.
     *
     * @param key configuration {@link Config#key() key} associated with the
     * {@code Config} node
     * @param stringValue original value to be filtered, never {@code null}
     * @return original value or filtered (changed) value, never {@code null}
     */
    String apply(Config.Key key, String stringValue);

    /**
     * Initializes the filter using the {@code Config} instance which the filter
     * will affect once {@code Config.Builder.build} completes.
     * <p>
     * The config system propagates any thrown exception to the application so its
     * invocation of {@code Config.Builder#build} fails.
     *
     * @param config {@code Config} instance under construction
     */
    default void init(Config config) {}

}

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

import java.util.Optional;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;
import io.helidon.config.spi.Source;

/**
 * Source options as a super set of all possible combinations of source implementation.
 * When used as a base class, together with {@link io.helidon.config.AbstractSourceBuilder}, you can set up any
 * source type.
 *
 * @see io.helidon.config.AbstractSourceBuilder
 * @see io.helidon.config.AbstractConfigSource
 * @see io.helidon.config.AbstractConfigSourceBuilder
 */
public class AbstractSource implements Source {
    // any source
    private final boolean optional;
    private final Optional<RetryPolicy> retryPolicy;
    // pollable source
    private final Optional<PollingStrategy> pollingStrategy;
    // watchable source
    private final Optional<ChangeWatcher<Object>> changeWatcher;

    /**
     * A new instance configured from the provided builder.
     * The builder is used to set the following:
     * <ul>
     *     <li>{@link #optional()} - for any source, whether the content must be present, or is optional</li>
     *     <li>{@link #retryPolicy()} - for any source, policy used to retry attempts at reading the content</li>
     *     <li>{@link #pollingStrategy()} - for {@link io.helidon.config.spi.PollableSource}, the polling strategy (if any)</li>
     *     <li>{@link #changeWatcher()} - for {@link io.helidon.config.spi.WatchableSource}, the change watcher (if any)</li>
     * </ul>
     * @param builder builder used to read the configuration options
     */
    @SuppressWarnings("unchecked")
    protected AbstractSource(AbstractSourceBuilder<?, ?> builder) {
        this.optional = builder.isOptional();
        this.pollingStrategy = builder.pollingStrategy();
        this.retryPolicy = builder.retryPolicy();
        this.changeWatcher = builder.changeWatcher().map(it -> (ChangeWatcher<Object>) it);
    }

    @Override
    public Optional<RetryPolicy> retryPolicy() {
        return retryPolicy;
    }

    @Override
    public boolean optional() {
        return optional;
    }

    /**
     * A polling strategy of this source, if it implements {@link io.helidon.config.spi.PollableSource} and has one
     * configured.
     *
     * @return polling strategy if any configured
     */
    protected Optional<PollingStrategy> pollingStrategy() {
        return pollingStrategy;
    }

    /**
     * A change watcher of this source, if it implements {@link io.helidon.config.spi.WatchableSource} and has one
     * configured.
     *
     * @return change watcher if any configured
     */
    protected Optional<ChangeWatcher<Object>> changeWatcher() {
        return changeWatcher;
    }

    /**
     * Returns universal id of source to be used to construct {@link #description()}.
     *
     * @return universal id of source
     */
    protected String uid() {
        return "";
    }

    @Override
    public String description() {
        return Source.super.description()
                + "[" + uid() + "]"
                + (optional() ? "?" : "")
                + (pollingStrategy().isEmpty() ? "" : "*");
    }
}

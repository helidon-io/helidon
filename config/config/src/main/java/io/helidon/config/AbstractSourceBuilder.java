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
import java.util.function.Supplier;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;
import io.helidon.config.spi.Source;
import io.helidon.config.spi.WatchableSource;

/**
 * Base class for common builder methods of a {@link io.helidon.config.spi.Source}
 *  implementation.
 *
 * @param <B> type of implementation class of the builder
 * @param <U> type of target for watchable sources, use {@code Void} if not supported
 */
public abstract class AbstractSourceBuilder<B extends AbstractSourceBuilder<B, U>, U> implements Source.Builder<B> {

    private PollingStrategy pollingStrategy;
    private RetryPolicy retryPolicy;
    private ChangeWatcher<U> changeWatcher;
    private boolean optional = false;

    @SuppressWarnings("unchecked")
    private final B me = (B) this;

    /**
     * Configure builder from meta configuration.
     * <p>
     * The following configuration options are supported:
     * <table class="config">
     * <caption>Optional configuration parameters</caption>
     * <tr>
     *     <th>key</th>
     *     <th>default value</th>
     *     <th>description</th>
     * </tr>
     * <tr>
     *     <td>optional</td>
     *     <td>{@code false}</td>
     *     <td>Configure to {@code true} if this source should not fail configuration setup when underlying data is missing.</td>
     * </tr>
     * <tr>
     *     <td>polling-strategy</td>
     *     <td>No polling strategy is added by default</td>
     *     <td>Meta configuration of a polling strategy to be used with this source, add configuration to {@code properties}
     *      sub node.</td>
     * </tr>
     * <tr>
     *     <td>change-watcher</td>
     *     <td>No change watcher is added by default</td>
     *     <td>Meta configuration of a change watcher to be used with this source, add configuration to {@code properties}
     *      sub node.</td>
     * </tr>
     * <tr>
     *     <td>retry-policy</td>
     *     <td>No retry policy is added by default</td>
     *     <td>Meta configuration of a retry policy to be used to load this source, add configuration to {@code properties}
     *      sub node.</td>
     * </tr>
     * </table>
     *
     * @param metaConfig meta configuration of this source
     * @return updated builder instance
     */
    @SuppressWarnings("unchecked")
    protected B config(Config metaConfig) {

        metaConfig.get("optional").asBoolean().ifPresent(this::optional);
        metaConfig.get("polling-strategy").as(MetaConfig::pollingStrategy).ifPresent(this::pollingStrategy);
        metaConfig.get("change-watcher").as(MetaConfig::changeWatcher).ifPresent(it -> changeWatcher((ChangeWatcher<U>) it));
        metaConfig.get("retry-policy").as(MetaConfig::retryPolicy).ifPresent(this::retryPolicy);

        return me;
    }

    @Override
    public B retryPolicy(Supplier<? extends RetryPolicy> policy) {
        this.retryPolicy = policy.get();
        return me;
    }

    @Override
    public B optional(boolean optional) {
        this.optional = optional;
        return me;
    }

    /**
     * Configure a change watcher.
     * This method must be exposed by builders of sources that change watching ({@link io.helidon.config.spi.WatchableSource}).
     * The type of the change watcher must match the type of the target of this source.
     *
     * @param changeWatcher change watcher to use, such as {@link io.helidon.config.FileSystemWatcher}
     * @return updated builder instance
     */
    protected B changeWatcher(ChangeWatcher<U> changeWatcher) {
        if (!(this instanceof WatchableSource.Builder)) {
            throw new ConfigException("You are attempting to configure a change watcher on a source builder that does "
                                              + "not support it: " + getClass().getName());
        }
        this.changeWatcher = changeWatcher;
        return me;
    }

    /**
     * Configure a polling strategy.
     * This method must be exposed by builders of sources that support polling.
     *
     * If you see this method as being protected in your builder, the source has removed
     * support for polling, such as {@link io.helidon.config.ClasspathConfigSource}.
     *
     * @param pollingStrategy polling strategy to use
     * @return updated builder instance
     */
    protected B pollingStrategy(PollingStrategy pollingStrategy) {
        if (!(this instanceof PollableSource.Builder)) {
            throw new ConfigException("You are attempting to configure a polling strategy on a source builder that does "
                                              + "not support it: " + getClass().getName());
        }

        this.pollingStrategy = pollingStrategy;
        return me;
    }

    Optional<PollingStrategy> pollingStrategy() {
        return Optional.ofNullable(pollingStrategy);
    }

    Optional<RetryPolicy> retryPolicy() {
        return Optional.ofNullable(retryPolicy);
    }

    Optional<ChangeWatcher<U>> changeWatcher() {
        return Optional.ofNullable(changeWatcher);
    }

    boolean isOptional() {
        return optional;
    }
}

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

import io.helidon.config.Config;

/**
 * Base class for common builder methods of a {@link io.helidon.config.spi.Source}
 *  implementation.
 *
 * @param <B> type of implementation class of the builder
 * @param <U> type of target for watchable sources, use {@code Void} if not supported
 */
public abstract class BaseSourceBuilder<B extends BaseSourceBuilder<B, U>, U> implements Source.Builder<B> {

    private PollingStrategy pollingStrategy;
    private RetryPolicy retryPolicy;
    private ChangeWatcher<U> changeWatcher;
    private boolean optional = false;

    @SuppressWarnings("unchecked")
    private B me = (B) this;

    protected B config(Config metaConfig) {
        // TODO get retry policy and everything else
        return me;
    }

    @Override
    public B retryPolicy(RetryPolicy policy) {
        this.retryPolicy = policy;
        return me;
    }

    @Override
    public B optional(boolean optional) {
        this.optional = optional;
        return me;
    }

    protected B changeWatcher(ChangeWatcher<U> changeWatcher) {
        this.changeWatcher = changeWatcher;
        return me;
    }

    protected B pollingStrategy(PollingStrategy pollingStrategy) {
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

    boolean optional() {
        return optional;
    }
}

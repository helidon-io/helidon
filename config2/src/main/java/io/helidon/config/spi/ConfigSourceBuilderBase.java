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

public abstract class ConfigSourceBuilderBase<T extends ConfigSourceBuilderBase<T, U>, U> implements ConfigSource.Builder<T> {

    private PollingStrategy pollingStrategy;
    private RetryPolicy retryPolicy;
    private ChangeWatcher<U> changeWatcher;
    private boolean optional = false;
    private ConfigParser parser;
    private String mediaType;
    @SuppressWarnings("unchecked")
    private T me = (T) this;

    @Override
    public T retryPolicy(RetryPolicy policy) {
        this.retryPolicy = policy;
        return me;
    }

    @Override
    public T optional(boolean optional) {
        this.optional = optional;
        return me;
    }

    protected T parser(ConfigParser parser) {
        this.parser = parser;
        return me;
    }

    protected T mediaType(String mediaType) {
        this.mediaType = mediaType;
        return me;
    }

    protected T changeWatcher(ChangeWatcher<U> changeWatcher) {
        this.changeWatcher = changeWatcher;
        return me;
    }

    protected T pollingStrategy(PollingStrategy pollingStrategy) {
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

    Optional<ConfigParser> parser() {
        return Optional.ofNullable(parser);
    }

    Optional<String> mediaType() {
        return Optional.ofNullable(mediaType);
    }
}

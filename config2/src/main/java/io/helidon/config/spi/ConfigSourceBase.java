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

public class ConfigSourceBase implements ConfigSource {
    private final boolean optional;
    private final Optional<PollingStrategy> pollingStrategy;
    private final Optional<RetryPolicy> retryPolicy;
    private final Optional<ChangeWatcher<?>> changeWatcher;
    private final Optional<String> mediaType;
    private final Optional<ConfigParser> parser;

    protected ConfigSourceBase(ConfigSourceBuilderBase<?, ?> builder) {
        this.optional = builder.optional();
        this.pollingStrategy = builder.pollingStrategy();
        this.retryPolicy = builder.retryPolicy();
        this.changeWatcher = builder.changeWatcher().map(it -> (ChangeWatcher<?>) it);
        mediaType = builder.mediaType();
        parser = builder.parser();
    }

    @Override
    public Optional<RetryPolicy> retryPolicy() {
        return retryPolicy;
    }

    @Override
    public boolean optional() {
        return optional;
    }

    protected Optional<PollingStrategy> pollingStrategy() {
        return pollingStrategy;
    }

    protected Optional<ChangeWatcher<?>> changeWatcher() {
        return changeWatcher;
    }

    protected Optional<String> mediaType() {
        return mediaType;
    }

    protected Optional<ConfigParser> parser() {
        return parser;
    }
}

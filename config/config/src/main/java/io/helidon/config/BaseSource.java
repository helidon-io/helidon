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

public class BaseSource implements Source {
    private final boolean optional;
    private final Optional<PollingStrategy> pollingStrategy;
    private final Optional<RetryPolicy> retryPolicy;
    private final Optional<ChangeWatcher<?>> changeWatcher;

    @SuppressWarnings("unchecked")
    protected BaseSource(BaseSourceBuilder builder) {
        this.optional = builder.optional();
        this.pollingStrategy = builder.pollingStrategy();
        this.retryPolicy = builder.retryPolicy();
        this.changeWatcher = builder.changeWatcher().map(it -> (ChangeWatcher<?>) it);
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

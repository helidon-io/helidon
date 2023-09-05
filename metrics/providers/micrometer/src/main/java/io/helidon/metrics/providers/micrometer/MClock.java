/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import io.micrometer.core.instrument.Clock;

/**
 * Wrapper for a {@link io.micrometer.core.instrument.Clock} when one is returned from
 * Micrometer.
 */
class MClock implements io.helidon.metrics.api.Clock {

    private final Clock delegate;

    private MClock(Clock delegate) {
        this.delegate = delegate;
    }

    static MClock create(Clock delegate) {
        return new MClock(delegate);
    }

    @Override
    public long wallTime() {
        return delegate.wallTime();
    }

    @Override
    public long monotonicTime() {
        return delegate.monotonicTime();
    }

    Clock delegate() {
        return delegate;
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }
}

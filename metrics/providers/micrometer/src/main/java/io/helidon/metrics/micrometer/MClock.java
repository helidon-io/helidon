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
package io.helidon.metrics.micrometer;

/**
 *
 */
class MClock implements io.helidon.metrics.api.Clock {

    static MClock create(io.micrometer.core.instrument.Clock delegate) {
        return new MClock(delegate);
    }

    private final io.micrometer.core.instrument.Clock delegate;

    private MClock(io.micrometer.core.instrument.Clock delegate) {
        this.delegate = delegate;
    }
    @Override
    public long wallTime() {
        return delegate.wallTime();
    }

    @Override
    public long monotonicTime() {
        return delegate.monotonicTime();
    }

    io.micrometer.core.instrument.Clock delegate() {
        return delegate;
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }
}

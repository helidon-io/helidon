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

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.distribution.ValueAtPercentile;

class MValueAtPercentile implements io.helidon.metrics.api.ValueAtPercentile {

    static MValueAtPercentile create(ValueAtPercentile delegate) {
        return new MValueAtPercentile(delegate);
    }

    private final ValueAtPercentile delegate;

    MValueAtPercentile(ValueAtPercentile delegate) {
        this.delegate = delegate;
    }

    @Override
    public double percentile() {
        return delegate.percentile();
    }

    @Override
    public double value() {
        return delegate.value();
    }

    @Override
    public double value(TimeUnit unit) {
        return delegate.value(unit);
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }
}

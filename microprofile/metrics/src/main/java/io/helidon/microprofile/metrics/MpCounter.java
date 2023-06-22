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
package io.helidon.microprofile.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementation of {@link org.eclipse.microprofile.metrics.Counter}.
 */
public class MpCounter extends MpMetric<Counter> implements org.eclipse.microprofile.metrics.Counter {

    /**
     * Creates a new instance.
     *
     * @param delegate meter which actually records data
     */
    MpCounter(Counter delegate, MeterRegistry meterRegistry) {
        super(delegate, meterRegistry);
    }

    @Override
    public void inc() {
        delegate().increment();
    }

    @Override
    public void inc(long n) {
        delegate().increment(n);
    }

    @Override
    public long getCount() {
        return (long) delegate().count();
    }
}

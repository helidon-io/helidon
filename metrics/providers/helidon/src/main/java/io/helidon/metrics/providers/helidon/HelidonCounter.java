/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.concurrent.atomic.LongAdder;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;

final class HelidonCounter extends HelidonMeter implements Counter {
    private final LongAdder count = new LongAdder();

    private HelidonCounter(Meter.Id id, Builder builder) {
        super(id, Type.COUNTER, builder);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    static HelidonCounter create(Meter.Id id, Builder builder) {
        return new HelidonCounter(id, builder);
    }

    @Override
    public void increment() {
        increment(1L);
    }

    @Override
    public void increment(long amount) {
        if (amount > 0) {
            count.add(amount);
        }
    }

    @Override
    public long count() {
        return count.sum();
    }

    @Override
    public String toString() {
        return stringPrefix() + "count=" + count() + "]";
    }

    static final class Builder extends HelidonMeter.AbstractBuilder<Counter.Builder, Counter> implements Counter.Builder {
        private Builder(String name) {
            super(name);
        }

        @Override
        Class<? extends Meter> meterType() {
            return Counter.class;
        }
    }
}

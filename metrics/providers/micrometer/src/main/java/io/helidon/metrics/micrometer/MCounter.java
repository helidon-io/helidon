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

import io.micrometer.core.instrument.Counter;

class MCounter extends MMeter<Counter> implements io.helidon.metrics.api.Counter {

    static Builder builder(String name) {
        return new Builder(name);
    }

    static MCounter create(Counter counter) {
        return new MCounter(counter);
    }

    private MCounter(Counter delegate) {
        super(delegate);
    }

    @Override
    public void increment() {
        delegate().increment();
    }

    @Override
    public void increment(double amount) {
        delegate().increment(amount);
    }

    @Override
    public double count() {
        return delegate().count();
    }

    static class Builder extends MMeter.Builder<Counter.Builder, Builder, MCounter>
                         implements io.helidon.metrics.api.Counter.Builder {

        private Builder(String name) {
            super(Counter.builder(name));
            prep(delegate()::tags,
                  delegate()::description,
                  delegate()::baseUnit);
        }

        // TODO remove if truly not used
//        @Override
//        MCounter register(MMeterRegistry mMeterRegistry) {
//
//            return MCounter.create(delegate().register(mMeterRegistry));
//        }
    }
}

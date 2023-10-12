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

import java.util.Optional;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;


class MCounter extends MMeter<io.micrometer.core.instrument.Counter> implements io.helidon.metrics.api.Counter {

    private MCounter(Meter.Id id, io.micrometer.core.instrument.Counter delegate, Builder builder) {
        super(id, delegate, builder);
    }

    private MCounter(Meter.Id id, io.micrometer.core.instrument.Counter delegate, Optional<String> scope) {
        super(id, delegate, scope);
    }

    /**
     * Creates a new builder for a wrapper around a to-be-created Micrometer counter, typically if the
     * developer is creating a counter using the Helidon API.
     *
     * @param name name of the new counter
     * @return new builder for a wrapper counter
     */
    static Builder builder(String name) {
        return new Builder(name, io.micrometer.core.instrument.Counter.builder(name));
    }

    /**
     * Creates a new wrapper counter around an existing Micrometer counter, typically if the developer has registered a
     * counter directly using the Micrometer API rather than through the Helidon adapter but we need to expose the counter
     * via a wrapper.
     *
     * @param counter the Micrometer counter
     * @param scope scope to apply
     * @return new wrapper around the counter
     */
    static MCounter create(Meter.Id id, io.micrometer.core.instrument.Counter counter, Optional<String> scope) {
        return new MCounter(id, counter, scope);
    }

    @Override
    public void increment() {
        delegate().increment();
    }

    @Override
    public void increment(long amount) {
        delegate().increment(amount);
    }

    @Override
    public long count() {
        return (long) delegate().count();
    }

    @Override
    public String toString() {
        return stringJoiner()
                .add("count=" + (long) delegate().count())
                .toString();
    }

    static class Builder extends
                         MMeter.Builder<io.micrometer.core.instrument.Counter.Builder,
                                 io.micrometer.core.instrument.Counter,
                                 Builder, MCounter>
            implements io.helidon.metrics.api.Counter.Builder {

        private Builder(String name, io.micrometer.core.instrument.Counter.Builder delegate) {
            super(name, delegate);
        }

        @Override
        public MCounter build(Meter.Id id, io.micrometer.core.instrument.Counter counter) {
            return new MCounter(id, counter, this);
        }

        @Override
        protected Builder delegateTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
            delegate().tags(tags);
            return identity();
        }

        @Override
        protected Builder delegateTag(String key, String value) {
            delegate().tag(key, value);
            return identity();
        }

        @Override
        protected Builder delegateDescription(String description) {
            delegate().description(description);
            return identity();
        }

        @Override
        protected Builder delegateBaseUnit(String baseUnit) {
            delegate().baseUnit(baseUnit);
            return identity();
        }

        @Override
        protected Class<? extends Meter> meterType() {
            return Counter.class;
        }
    }
}

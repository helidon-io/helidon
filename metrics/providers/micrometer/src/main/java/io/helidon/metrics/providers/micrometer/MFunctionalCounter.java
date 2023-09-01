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
import java.util.function.Function;

import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Meter;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Tag;

class MFunctionalCounter extends MMeter<io.micrometer.core.instrument.FunctionCounter>
        implements io.helidon.metrics.api.FunctionalCounter {

    private MFunctionalCounter(Meter.Id id,
                               io.micrometer.core.instrument.FunctionCounter delegate,
                               MFunctionalCounter.Builder<?> builder) {
        super(id, delegate, builder);
    }

    private MFunctionalCounter(Meter.Id id, io.micrometer.core.instrument.FunctionCounter delegate, Optional<String> scope) {
        super(id, delegate, scope);
    }

    /**
     * Creates a new builder for a wrapper around a to-be-created Micrometer function counter, typically if
     * the developer is creating a function counter using the Helidon API.
     *
     * @param name        name of the new counter
     * @param stateObject object which provides the counter value
     * @param fn          function which, when applied to the state object, returns the counter value
     * @param <T>         type of the state object
     * @return new builder for a wrapper counter
     */
    static <T> Builder<T> builder(String name, T stateObject, Function<T, Long> fn) {
        return new Builder<>(name, stateObject, fn);
    }

    static <T> Builder<T> builderFrom(FunctionalCounter.Builder<T> origin) {
        MFunctionalCounter.Builder<T> builder = builder(origin.name(),
                                                        origin.stateObject(),
                                                        origin.fn());
        return builder.from(origin);
    }

    static MFunctionalCounter create(Meter.Id id,
                                     io.micrometer.core.instrument.FunctionCounter functionCounter,
                                     Optional<String> scope) {
        return new MFunctionalCounter(id, functionCounter, scope);
    }

    @Override
    public long count() {
        return (long) delegate().count();
    }

    @Override
    public String toString() {
        return stringJoiner()
                .add("count=" + count())
                .toString();
    }

    static class Builder<T> extends
                            MMeter.Builder<io.micrometer.core.instrument.FunctionCounter.Builder<T>,
                                    io.micrometer.core.instrument.FunctionCounter, Builder<T>, MFunctionalCounter>
            implements FunctionalCounter.Builder<T> {

        private final T stateObject;
        private final Function<T, Long> fn;

        Builder(String name, T stateObject, Function<T, Long> fn) {
            super(name, FunctionCounter.builder(name, stateObject, t -> fn.apply(t).doubleValue()));
            this.stateObject = stateObject;
            this.fn = fn;
        }

        @Override
        protected Builder<T> delegateTags(Iterable<Tag> tags) {
            delegate().tags(tags);
            return identity();
        }

        @Override
        protected Builder<T> delegateTag(String key, String value) {
            delegate().tag(key, value);
            return identity();
        }

        @Override
        protected Builder<T> delegateDescription(String description) {
            delegate().description(description);
            return identity();
        }

        @Override
        protected Builder<T> delegateBaseUnit(String baseUnit) {
            delegate().baseUnit(baseUnit);
            return identity();
        }

        @Override
        public T stateObject() {
            return stateObject;
        }

        @Override
        public Function<T, Long> fn() {
            return fn;
        }

        @Override
        public MFunctionalCounter build(Meter.Id id, io.micrometer.core.instrument.FunctionCounter functionCounter) {
            return new MFunctionalCounter(id, functionCounter, this);
        }

        @Override
        protected Class<? extends Meter> meterType() {
            return FunctionalCounter.class;
        }
    }
}

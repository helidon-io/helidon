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

import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.FunctionalCounter;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Tag;

class MFunctionalCounter extends MMeter<FunctionCounter> implements io.helidon.metrics.api.FunctionalCounter {

    /**
     * Creates a new builder for a wrapper around a to-be-created Micrometer function counter, typically if
     * the developer is creating a function counter using the Helidon API.
     *
     * @param name name of the new counter
     * @param stateObject object which provides the counter value
     * @param fn function which, when applied to the state object, returns the counter value
     * @return new builder for a wrapper counter
     * @param <T> type of the state object
     */
    static <T> Builder<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return new Builder<>(name, stateObject, fn);
    }

    static MFunctionalCounter create(FunctionCounter functionCounter) {
        return new MFunctionalCounter(functionCounter);
    }

    private MFunctionalCounter(FunctionCounter delegate, MFunctionalCounter.Builder<?> builder) {
        super(delegate, builder);
    }

    private MFunctionalCounter(FunctionCounter delegate) {
        super(delegate);
    }

    @Override
    public long count() {
        return (long) delegate().count();
    }
    static class Builder<T> extends MMeter.Builder<FunctionCounter.Builder<T>, FunctionCounter, Builder<T>, MFunctionalCounter>
                implements FunctionalCounter.Builder<T> {

        private final T stateObject;
        private final ToDoubleFunction<T> fn;

        Builder(String name, T stateObject, ToDoubleFunction<T> fn) {
            super(name, FunctionCounter.builder(name, stateObject, fn));
            this.stateObject = stateObject;
            this.fn = fn;
        }

        @Override
        protected Builder<T> delegateTags(Iterable<Tag> tags) {
            delegate().tags(tags);
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
        public ToDoubleFunction<T> fn() {
            return fn;
        }

        @Override
        public MFunctionalCounter build(FunctionCounter functionCounter) {
            return new MFunctionalCounter(functionCounter, this);
        }
    }
}

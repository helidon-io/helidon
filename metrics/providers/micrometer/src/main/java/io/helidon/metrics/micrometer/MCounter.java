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
import io.micrometer.core.instrument.Tag;

class MCounter extends MMeter<Counter> implements io.helidon.metrics.api.Counter {

    /**
     * Creates a new builder for a wrapper around a to-be-created Micrometer counter, typically if the
     * developer is creating a counter using the Helidon API.
     *
     * @param name name of the new counter
     * @return new builder for a wrapper counter
     */
    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Creates a new wrapper counter around an existing Micrometer counter, typically if the developer has registered a
     * counter directly using the Micrometer API rather than through the Helidon adapter but we need to expose the counter
     * via a wrapper.
     *
     * @param counter the Micrometer counter
     * @return new wrapper around the counter
     */
    static MCounter create(Counter counter) {
        return new MCounter(counter);
    }

    private MCounter(Counter delegate, Builder builder) {
        super(delegate, builder);
    }

    private MCounter(Counter delegate) {
        super(delegate);
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

    static class Builder extends MMeter.Builder<Counter.Builder, Counter, Builder, MCounter>
                    implements io.helidon.metrics.api.Counter.Builder {

        private Builder(String name) {
            super(Counter.builder(name));
        }

        @Override
        public MCounter build(Counter counter) {
            return new MCounter(counter, this);
        }

        @Override
        protected Builder delegateTags(Iterable<Tag> tags) {
            delegate().tags(tags);
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
    }

//    static class MFunctionCounter extends MMeter<FunctionCounter> implements io.helidon.metrics.api.Counter {
//
//        static <T> Builder<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
//            return new Builder<>(name, stateObject, fn);
//        }
//
//        static class Builder<T> extends MMeter.Builder<FunctionCounter.Builder<T>, FunctionCounter,
//                MFunctionCounter.Builder<T>, MFunctionCounter> {
//
//            private final T stateObject;
//            private final ToDoubleFunction<T> fn;
//
//            Builder(String name, T stateObject, ToDoubleFunction<T> fn) {
//                super(FunctionCounter.builder(name, stateObject, fn));
//                this.stateObject = stateObject;
//                this.fn = fn;
//            }
//
//            @Override
//            protected Builder<T> delegateTags(Iterable<Tag> tags) {
//                delegate().tags(tags);
//                return identity();
//            }
//
//            @Override
//            protected Builder<T> delegateDescription(String description) {
//                delegate().description(description);
//                return identity();
//            }
//
//            @Override
//            protected Builder<T> delegateBaseUnit(String baseUnit) {
//                delegate().baseUnit(baseUnit);
//                return identity();
//            }
//
//            @Override
//            public MFunctionCounter build(FunctionCounter functionCounter) {
//                return new MFunctionCounter(functionCounter, this);
//            }
//        }
//
//        private MFunctionCounter(FunctionCounter delegate, Builder<?> builder) {
//            super(delegate, builder);
//        }
//
//        private MFunctionCounter(FunctionCounter delegate) {
//            super(delegate);
//        }
//
//        @Override
//        public void increment() {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public void increment(long amount) {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public long count() {
//            return (long) delegate().count();
//        }
//    }


}

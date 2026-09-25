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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Meter;

final class HelidonFunctionalCounter extends HelidonMeter implements FunctionalCounter {
    private final Supplier<Long> countSupplier;

    private <T> HelidonFunctionalCounter(Meter.Id id, Builder<T> builder) {
        super(id, Type.COUNTER, builder);
        T stateObject = builder.stateObject();
        Function<T, Long> fn = builder.fn();
        this.countSupplier = () -> Objects.requireNonNull(fn.apply(stateObject));
    }

    static <T> Builder<T> builder(String name, T stateObject, Function<T, Long> fn) {
        return new Builder<>(name, stateObject, fn);
    }

    static <T> HelidonFunctionalCounter create(Meter.Id id, Builder<T> builder) {
        return new HelidonFunctionalCounter(id, builder);
    }

    @Override
    public long count() {
        return countSupplier.get();
    }

    static final class Builder<T> extends HelidonMeter.AbstractBuilder<FunctionalCounter.Builder<T>, FunctionalCounter>
            implements FunctionalCounter.Builder<T> {
        private final T stateObject;
        private final Function<T, Long> fn;

        private Builder(String name, T stateObject, Function<T, Long> fn) {
            super(name);
            this.stateObject = Objects.requireNonNull(stateObject);
            this.fn = Objects.requireNonNull(fn);
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
        Class<? extends Meter> meterType() {
            return FunctionalCounter.class;
        }
    }
}

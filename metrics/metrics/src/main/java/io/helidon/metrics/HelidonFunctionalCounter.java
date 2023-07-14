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
package io.helidon.metrics;

import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Implementation of a counter wrapper around an object capable of providing a double value.
 *
 * @param <T> type of the object providing the value
 */
class HelidonFunctionalCounter<T> extends MetricImpl implements Counter {

    /**
     * Creates a new functional counter.
     *
     * @param meterRegistry registry in which to add the functional counter
     * @param scope scope of the registry
     * @param metadata metadata describing the functional counter
     * @param origin object which provides the counter value
     * @param function function which, when applied to the origin, yields the counter value
     * @param tags further identifies the functional counter
     * @return new functional counter
     * @param <T> type of the origin
     */
    static <T> HelidonFunctionalCounter<T> create(MeterRegistry meterRegistry,
                                               String scope,
                                               Metadata metadata,
                                               T origin,
                                               ToDoubleFunction<T> function,
                                               Tag... tags) {
        return new HelidonFunctionalCounter<>(scope,
                                  metadata,
                                  origin,
                                  function,
                                  io.micrometer.core.instrument.FunctionCounter.builder(metadata.getName(), origin, function)
                                          .baseUnit(sanitizeUnit(metadata.getUnit()))
                                          .description(metadata.getDescription())
                                          .tags(allTags(scope, tags))
                                          .register(meterRegistry));
    }

    private final T origin;
    private final ToDoubleFunction<T> function;
    private final FunctionCounter delegate;

    private HelidonFunctionalCounter(String scope,
                                     Metadata metadata,
                                     T origin,
                                     ToDoubleFunction<T> function,
                                     FunctionCounter delegate) {
        super(scope, metadata);
        this.origin = origin;
        this.function = function;
        this.delegate = delegate;
    }

    @Override
    public FunctionCounter delegate() {
        return delegate;
    }

    @Override
    public void inc() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void inc(long n) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getCount() {
        return (long) function.applyAsDouble(origin);
    }
}

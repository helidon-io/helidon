/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;

/**
 * Gauge implementation.
 */
final class HelidonGauge<T extends Number> extends MetricImpl implements Gauge<T> {
    private final Supplier<T> value;

    private HelidonGauge(String registryType, Metadata metadata, Gauge<T> metric) {
        super(registryType, metadata);

        value = metric::getValue;
    }

    static <S extends Number> HelidonGauge<S> create(String registryType, Metadata metadata,
            Gauge<S> metric) {
        return new HelidonGauge<>(registryType, metadata, metric);
    }

    static <T, S extends Number> HelidonGauge<S> create(String registryType, Metadata metadata,
                                                        T object,
                                                        Function<T, S> func) {
        return new HelidonGauge<>(registryType, metadata, () -> func.apply(object));
    }

    static <T, S extends Number> HelidonGauge<S> create(String registryType,
                                                         Metadata metadata,
                                                         Supplier<S> valueSupplier) {
        return new HelidonGauge<>(registryType, metadata, valueSupplier::get);
    }

    @Override
    public T getValue() {
        return value.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        HelidonGauge<?> that = (HelidonGauge<?>) o;
        return getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getValue());
    }

    @Override
    protected String toStringDetails() {
        return ", value='" + getValue() + '\'';
    }
}

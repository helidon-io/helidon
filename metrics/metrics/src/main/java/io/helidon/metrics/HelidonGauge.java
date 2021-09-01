/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Gauge implementation.
 */
final class HelidonGauge<T /* extends Number */> extends MetricImpl implements Gauge<T> {
    // TODO uncomment above once MP metrics enforces the Number restriction
    private final Supplier<T> value;

    private HelidonGauge(String registryType, Metadata metadata, Gauge<T> metric) {
        super(registryType, metadata);

        value = metric::getValue;
    }

    static <S /* extends Number */> HelidonGauge<S> create(String registryType, Metadata metadata,
            Gauge<S> metric) {
        // TODO uncomment above once MP metrics enforces the Number restriction
        return new HelidonGauge<>(registryType, metadata, metric);
    }

    @Override
    public T getValue() {
        return value.get();
    }

    @Override
    public String prometheusNameWithUnits(MetricID metricID) {
        return prometheusNameWithUnits(metricID.getName(), getUnits().getPrometheusUnit());
    }

    @Override
    public String prometheusValue() {
        return getUnits().convert(getValue()).toString();
    }

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        // TODO uncomment 'value' declaration below and remove 'untypedValue' once MP metrics enforces restriction
        // T value = getValue();
        T untypedValue = getValue();
        String nameWithTags = jsonFullKey(metricID);

        // TODO remove following 'if' and 'value' assignment once MP metrics enforces restriction,
        // promoting the nested 'if' one level.
        if (untypedValue instanceof Number) {
            Number value = (Number) untypedValue;
            if (value instanceof AtomicInteger) {
                builder.add(nameWithTags, value.doubleValue());
            } else if (value instanceof AtomicLong) {
                builder.add(nameWithTags, value.longValue());
            } else if (value instanceof BigDecimal) {
                builder.add(nameWithTags, (BigDecimal) value);
            } else if (value instanceof BigInteger) {
                builder.add(nameWithTags, (BigInteger) value);
            } else if (value instanceof Byte) {
                builder.add(nameWithTags, value.intValue());
            } else if (value instanceof Double) {
                builder.add(nameWithTags, (Double) value);
            } else if (value instanceof DoubleAccumulator) {
                builder.add(nameWithTags, value.doubleValue());
            } else if (value instanceof DoubleAdder) {
                builder.add(nameWithTags, value.doubleValue());
            } else if (value instanceof Float) {
                builder.add(nameWithTags, value.floatValue());
            } else if (value instanceof Integer) {
                builder.add(nameWithTags, (Integer) value);
            } else if (value instanceof Long) {
                builder.add(nameWithTags, (Long) value);
            } else if (value instanceof LongAccumulator) {
                builder.add(nameWithTags, value.longValue());
            } else if (value instanceof LongAdder) {
                builder.add(nameWithTags, value.longValue());
            } else if (value instanceof Short) {
                builder.add(nameWithTags, value.intValue());
            } else {
                // Might be a developer-provided class which extends Number.
                builder.add(nameWithTags, value.doubleValue());
            }
        // TODO remove following 'else' and 'builder.add' once MP metrics enforces restriction
        } else {
            builder.add(nameWithTags, String.valueOf(value));
        }
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

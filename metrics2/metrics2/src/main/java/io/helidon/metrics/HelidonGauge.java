/*
 * Copyright (c) 2018,2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Gauge implementation.
 */
final class HelidonGauge<T> extends MetricImpl implements Gauge<T> {
    private final Supplier<T> value;

    private HelidonGauge(String registryType, Metadata metadata, Gauge<T> metric) {
        super(registryType, metadata);

        value = metric::getValue;
    }

    static HelidonGauge<?> create(String registryType, Metadata metadata, Gauge<?> metric) {
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
        T value = getValue();
        String nameWithTags = jsonFullKey(metricID);

        if (value instanceof String) {
            builder.add(nameWithTags, (String) value);
        } else if (value instanceof BigInteger) {
            builder.add(nameWithTags, (BigInteger) value);
        } else if (value instanceof BigDecimal) {
            builder.add(nameWithTags, (BigDecimal) value);
        } else if (value instanceof Integer) {
            builder.add(nameWithTags, (Integer) value);
        } else if (value instanceof Long) {
            builder.add(nameWithTags, (Long) value);
        } else if (value instanceof Double) {
            builder.add(nameWithTags, (Double) value);
        } else if (value instanceof Boolean) {
            builder.add(nameWithTags, (Boolean) value);
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
}

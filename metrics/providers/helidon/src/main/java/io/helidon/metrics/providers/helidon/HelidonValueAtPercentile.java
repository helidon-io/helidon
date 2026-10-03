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
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.ValueAtPercentile;

final class HelidonValueAtPercentile implements ValueAtPercentile {
    private final double percentile;
    private final double value;

    HelidonValueAtPercentile(double percentile, double value) {
        this.percentile = percentile;
        this.value = value;
    }

    @Override
    public double percentile() {
        return percentile;
    }

    @Override
    public double value() {
        return value;
    }

    @Override
    public double value(TimeUnit unit) {
        return HelidonTypes.toTimeUnit(value, Objects.requireNonNull(unit));
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return Objects.requireNonNull(c).cast(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValueAtPercentile valueAtPercentile)) {
            return false;
        }
        return Double.compare(percentile, valueAtPercentile.percentile()) == 0
                && Double.compare(value, valueAtPercentile.value()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(percentile, value);
    }

    @Override
    public String toString() {
        return "HelidonValueAtPercentile[percentile=" + percentile + ", value=" + value + "]";
    }
}

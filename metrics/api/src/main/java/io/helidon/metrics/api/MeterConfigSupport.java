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

package io.helidon.metrics.api;

import java.time.Duration;

import io.helidon.builder.api.Prototype;

final class MeterConfigSupport {
    private MeterConfigSupport() {
    }

    private static void validateDuration(String setting, Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Meter " + setting + " must be positive: " + duration);
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Meter " + setting + " must be representable in nanoseconds: " + duration, e);
        }
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<MeterConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(MeterConfig.BuilderBase<?, ?> builder) {
            builder.namePattern().ifPresent(pattern -> {
                if (pattern.pattern().isBlank()) {
                    throw new IllegalArgumentException("Meter configuration name-pattern must not be blank");
                }
            });
            builder.percentiles().ifPresent(percentiles -> {
                for (double percentile : percentiles) {
                    if (!Double.isFinite(percentile) || percentile < 0 || percentile > 1) {
                        throw new IllegalArgumentException("Meter percentile must be finite and between 0.0 and 1.0: "
                                                                   + percentile);
                    }
                }
            });
            builder.buckets().ifPresent(buckets -> buckets.forEach(bucket -> validateDuration("bucket", bucket)));
            builder.minimumExpectedValue().ifPresent(min -> validateDuration("minimum-expected-value", min));
            builder.maximumExpectedValue().ifPresent(max -> validateDuration("maximum-expected-value", max));
            if (builder.minimumExpectedValue().isPresent() && builder.maximumExpectedValue().isPresent()
                    && builder.minimumExpectedValue().get().compareTo(builder.maximumExpectedValue().get()) > 0) {
                throw new IllegalArgumentException("Meter minimum-expected-value must not exceed maximum-expected-value");
            }
        }
    }
}

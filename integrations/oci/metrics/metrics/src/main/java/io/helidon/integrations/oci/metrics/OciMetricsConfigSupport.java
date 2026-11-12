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

package io.helidon.integrations.oci.metrics;

import java.time.Duration;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

class OciMetricsConfigSupport {

    private OciMetricsConfigSupport() {
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<OciMetricsConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(OciMetricsConfig.BuilderBase<?, ?> target) {
            var errors = Errors.collector();
            validateDuration(errors, target.initialDelay(), "initial-delay", false);
            validateDuration(errors, target.delay(), "delay", true);
            validateDuration(errors, target.batchDelay(), "batch-delay", false);
            if (target.batchSize() <= 0) {
                errors.fatal(getClass(), "batch-size must be greater than zero");
            }
            errors.collect().checkValid();
        }

        private void validateDuration(Errors.Collector errors, Duration value, String key, boolean positive) {
            if (value.isNegative() || (positive && value.isZero())) {
                errors.fatal(getClass(), key + (positive ? " must be positive" : " must not be negative"));
            }
            try {
                value.toNanos();
            } catch (ArithmeticException e) {
                errors.fatal(getClass(), key + " must be representable as a signed 64-bit number of nanoseconds");
            }
        }
    }
}

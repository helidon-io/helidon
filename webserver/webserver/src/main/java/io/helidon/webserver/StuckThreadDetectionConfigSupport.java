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

package io.helidon.webserver;

import java.time.Duration;

import io.helidon.builder.api.Prototype;

final class StuckThreadDetectionConfigSupport {
    private StuckThreadDetectionConfigSupport() {
    }

    static final class BuilderDecorator
            implements Prototype.BuilderDecorator<StuckThreadDetectionConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(StuckThreadDetectionConfig.BuilderBase<?, ?> target) {
            validateDuration(target.threshold(), "threshold");
            validateDuration(target.checkPeriod(), "check-period");
        }

        private static void validateDuration(Duration duration, String option) {
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("Stuck thread detection " + option + " must be positive");
            }
            try {
                duration.toNanos();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Stuck thread detection " + option + " is too large", e);
            }
        }
    }
}

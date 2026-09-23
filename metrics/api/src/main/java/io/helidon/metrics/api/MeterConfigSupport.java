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

import io.helidon.builder.api.Prototype;

final class MeterConfigSupport {
    private MeterConfigSupport() {
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<MeterConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(MeterConfig.BuilderBase<?, ?> builder) {
            builder.name().ifPresent(name -> {
                if (name.isBlank()) {
                    throw new IllegalArgumentException("Meter configuration name must not be blank");
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
        }
    }
}

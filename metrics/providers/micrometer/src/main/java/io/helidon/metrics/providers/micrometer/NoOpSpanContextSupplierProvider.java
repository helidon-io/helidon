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
package io.helidon.metrics.providers.micrometer;

import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;

/**
 * No-op implementation for providing exemplar trace information.
 */
class NoOpSpanContextSupplierProvider implements SpanContextSupplierProvider {
    @Override
    public SpanContextSupplier get() {
        return new SpanContextSupplier() {
            @Override
            public String getTraceId() {
                return null;
            }

            @Override
            public String getSpanId() {
                return null;
            }

            @Override
            public boolean isSampled() {
                return false;
            }
        };
    }
}

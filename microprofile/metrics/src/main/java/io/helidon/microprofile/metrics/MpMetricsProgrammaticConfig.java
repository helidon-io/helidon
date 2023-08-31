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
package io.helidon.microprofile.metrics;

import java.util.Optional;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;

/**
 * MP implementation of metrics programmatic settings.
 */
public class MpMetricsProgrammaticConfig implements MetricsProgrammaticConfig {

    /**
     * Creates a new instance (explicit for style checking and service loading).
     */
    public MpMetricsProgrammaticConfig() {
    }

    @Override
    public Optional<String> scopeTagName() {
        return Optional.of("mp_scope");
    }

    @Override
    public Optional<String> appTagName() {
        return Optional.of("mp_app");
    }

    @Override
    public Optional<String> scopeDefaultValue() {
        return Optional.of(Meter.Scope.DEFAULT);
    }
}

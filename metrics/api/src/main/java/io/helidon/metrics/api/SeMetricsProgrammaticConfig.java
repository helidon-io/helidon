/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Optional;

import io.helidon.metrics.spi.MetricsProgrammaticConfig;

/**
 * Provides SE defaults for config values defaulted in a flavor-dependent way.
 */
public class SeMetricsProgrammaticConfig implements MetricsProgrammaticConfig {

    /**
     * For service loading.
     */
    public SeMetricsProgrammaticConfig() {
    }

    @Override
    public Optional<String> scopeTagName() {
        return Optional.of("scope");
    }

    @Override
    public Optional<String> appTagName() {
        return Optional.of("app");
    }

    @Override
    public Optional<String> scopeDefaultValue() {
        return Optional.of(Meter.Scope.DEFAULT);
    }
}

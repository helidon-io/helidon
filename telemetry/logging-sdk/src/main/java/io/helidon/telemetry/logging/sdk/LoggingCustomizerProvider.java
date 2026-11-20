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
package io.helidon.telemetry.logging.sdk;

import java.util.Objects;

import io.helidon.common.Api;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

/**
 * Applies export-time suppression through OpenTelemetry's autoconfiguration SPI.
 */
@Api.Internal
public final class LoggingCustomizerProvider implements AutoConfigurationCustomizerProvider {
    /**
     * Service-loader constructor.
     */
    public LoggingCustomizerProvider() {
    }

    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        Objects.requireNonNull(customizer, "customizer")
                .addLogRecordExporterCustomizer((exporter, _) -> LogRecordExporters.suppressLogging(exporter));
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}

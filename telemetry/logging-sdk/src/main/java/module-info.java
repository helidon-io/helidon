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
/**
 * OpenTelemetry SDK export integration, separate from the API-only logging bridge contracts.
 */
module io.helidon.telemetry.logging.sdk {
    requires io.helidon.common;
    requires io.helidon.telemetry.logging;
    requires transitive io.opentelemetry.sdk.logs;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.sdk.autoconfigure.spi;

    exports io.helidon.telemetry.logging.sdk;

    provides io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider
            with io.helidon.telemetry.logging.sdk.LoggingCustomizerProvider;
}

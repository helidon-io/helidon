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

package io.helidon.telemetry.otelconfig;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Public access to OpenTelemetry as managed via Helidon config and builders.
 */
public interface HelidonOpenTelemetry {

    /**
     * Top-level config key for telemetry settings.
     */
    String CONFIG_KEY = "telemetry";

    /**
     * Returns the {@link io.opentelemetry.api.OpenTelemetry} instance managed by Helidon.
     * @return the OpenTelemetry instance
     */
    OpenTelemetry openTelemetry();
}

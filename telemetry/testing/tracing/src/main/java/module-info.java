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

/**
 * Telemetry tracing testing support.
 */
module io.helidon.telemetry.testing.tracing {

    requires java.logging;

    requires io.helidon.common.testing.junit5;

    requires io.opentelemetry.exporter.logging.otlp;
    requires jakarta.json;
    requires hamcrest.all;

}

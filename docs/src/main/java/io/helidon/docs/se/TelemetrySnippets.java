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

package io.helidon.docs.se;
// tag::snippet_1_imports[]
import java.util.Map;
import io.helidon.telemetry.otelconfig.HelidonOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
// end::snippet_1_imports[]

class TelemetrySnippets {

    // Example if user code creates an OpenTelemetry explicitly and then wants to make it known throughout OTel and Helidon.
    void snippet_1() {

        // tag::snippet_1[]

        // Application code using the OpenTelemetry API or the Helidon OpenTelemetry API or both.
        OpenTelemetry customOpenTelemetry = prepareOpenTelemetry();

        // App code to build any tags to be applied to every span.
        Map<String, String> tags = prepareTags();

        HelidonOpenTelemetry.global(customOpenTelemetry,
                                    "your-service-name",
                                    tags);

        // end::snippet_1[]
    }

    private OpenTelemetry prepareOpenTelemetry() {
        return HelidonOpenTelemetry.builder().build().openTelemetry();
    }

    private Map<String, String> prepareTags() {
        return Map.of();
    }
}

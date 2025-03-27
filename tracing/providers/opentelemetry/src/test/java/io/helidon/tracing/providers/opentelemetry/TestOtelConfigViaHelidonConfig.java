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
package io.helidon.tracing.providers.opentelemetry;

import java.util.Map;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class TestOtelConfigViaHelidonConfig {

    @Test
    void testOtlpHttpSpanExporter() {
        var yaml = """
                tracing:
                  service: "test-tracing"
                  propagation: ["tracecontext","b3"]
                  span-processors:
                    - exporter-name: "@default"
                    - exporter-name: test-zipkin-exporter
                  span-exporters:
                    - type: otlp
                      protocol: http/protobuf
                      compression: gzip
                    - type: zipkin
                      name: test-zipkin-exporter
                      protocol: http""";

        Config config = Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_YAML));
        OpenTelemetryTracerBuilder builder = OpenTelemetryTracer.builder().config(config.get("tracing"));

        Tracer tracer = builder.build();

    }
}

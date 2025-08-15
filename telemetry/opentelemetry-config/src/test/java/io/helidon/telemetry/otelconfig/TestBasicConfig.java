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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.notNullValue;

class TestBasicConfig {

    static final Config config = Config.just(ConfigSources.create(
            """
                    telemetry:
                      service: "test-otel"
                      global: false
                      signals:
                        tracing:
                          attributes:
                            attr1: val1
                          boolean-attributes:
                            attr2: true
                          numeric-attributes:
                            attr3: 24.5
                          sampler:
                            type: "always_off"
                          exporters:
                            - type: otlp
                              protocol: http/proto
                              name: my-oltp
                            - type: zipkin
                          processors:
                            - max-queue-size: 21
                              type: batch
                            - max-queue-size: 22
                              type: simple
                              exporters: ["my-oltp"]
                    """,
            MediaTypes.APPLICATION_YAML));

    @Test
    void testTelemetryWithTracer() {

        OpenTelemetryConfig openTelemetryConfig = OpenTelemetryConfig.create(config.get("telemetry"));

        OpenTelemetryTracingConfig openTelemetryTracingConfig = openTelemetryConfig.tracingConfig().orElseThrow();
        assertThat("Helidon OTel tracing", openTelemetryTracingConfig, is(notNullValue()));

        assertThat("Exporters",
                   openTelemetryTracingConfig.exporterConfigs().values(),
                   allOf(hasItems(instanceOf(OtlpHttpSpanExporter.class),
                                  instanceOf(ZipkinSpanExporter.class)),
                         iterableWithSize(2)));

        assertThat("Sampler",
                   openTelemetryConfig.openTelemetrySdk().getSdkTracerProvider().getSampler().toString(),
                   is("AlwaysOffSampler"));



    }

    @Test
    void testTelemetryDefaults() {
        var config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: "test-otel"
                        """,
                MediaTypes.APPLICATION_YAML));

            OpenTelemetryConfig openTelemetry = OpenTelemetryConfig.create(config.get("telemetry"));

            assertThat("Global", openTelemetry.global(), is(true));
            assertThat("Enabled", openTelemetry.enabled(), is(true));
        }
}

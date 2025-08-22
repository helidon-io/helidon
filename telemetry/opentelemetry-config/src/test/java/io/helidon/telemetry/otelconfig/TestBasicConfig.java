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

import java.time.Duration;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
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
                            strings:
                              attr1: 12
                              attr5: anything
                            longs:
                              attr2: 12
                            doubles:
                              attr3: 24.5
                            booleans:
                              attr4: true
                          sampler:
                            type: "always_off"
                          exporters:
                            - type: otlp
                              protocol: http/proto
                              retry-policy:
                                max-attempts: 4
                                max-backoff: "PT1m"
                              name: my-otlp
                            - type: zipkin
                          processors:
                            - max-queue-size: 21
                              type: batch
                            - max-queue-size: 22
                              type: simple
                              exporters: ["my-otlp"]
                    """,
            MediaTypes.APPLICATION_YAML));

    @Test
    void testTelemetryWithTracer() {

        OpenTelemetryConfig openTelemetryConfig = OpenTelemetryConfig.create(config.get("telemetry"));

        RetryPolicyConfig retryPolicyConfig = RetryPolicyConfig.create(config.get(
                "telemetry.signals.tracing.exporters.0.retry-policy"));
        assertThat("Retry policy config max-attempts",
                   retryPolicyConfig.maxAttempts(),
                   OptionalMatcher.optionalValue(is(4)));
        assertThat("Retry policy config max-backoff",
                   retryPolicyConfig.maxBackoff(),
                   OptionalMatcher.optionalValue(is(Duration.ofMinutes(1))));

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

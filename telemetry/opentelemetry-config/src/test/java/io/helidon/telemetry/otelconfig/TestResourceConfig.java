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

package io.helidon.telemetry.otelconfig;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.ContainerAttributes;
import io.opentelemetry.semconv.DeploymentAttributes;
import io.opentelemetry.semconv.ServiceAttributes;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

class TestResourceConfig {

    private static final AttributeKey<String> CUSTOM_STRING = AttributeKey.stringKey("custom.string");
    private static final AttributeKey<Long> CUSTOM_LONG = AttributeKey.longKey("custom.long");
    private static final AttributeKey<Double> CUSTOM_DOUBLE = AttributeKey.doubleKey("custom.double");
    private static final AttributeKey<Boolean> CUSTOM_BOOLEAN = AttributeKey.booleanKey("custom.boolean");
    private static final AttributeKey<String> PRECEDENCE = AttributeKey.stringKey("custom.precedence");

    @Test
    void testDeclarativeResourceAttributesOnAllSignals() {
        var config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: checkout
                          global: false
                          resource:
                            service-namespace: retail
                            service-instance-id: checkout-7f3a
                            deployment-environment-name: production
                            container-id: container-123
                            container-image-name: registry.example.com/retail/checkout
                            container-image-tags: [latest, "2.1"]
                            container-image-repo-digests:
                              - "checkout@sha256:first"
                              - "checkout@sha256:second"
                            attributes:
                              strings:
                                custom.string: common
                                custom.precedence: common
                                service.name: common-service
                                service.namespace: common-namespace
                              longs:
                                custom.long: 3
                              doubles:
                                custom.double: 4.5
                              booleans:
                                custom.boolean: true
                          signals:
                            tracing:
                              attributes:
                                strings:
                                  custom.precedence: tracing
                                  service.name: tracing-service
                                  service.namespace: tracing-namespace
                            metrics:
                              attributes:
                                strings:
                                  custom.precedence: metrics
                                  service.name: metrics-service
                                  service.namespace: metrics-namespace
                            logging:
                              attributes:
                                strings:
                                  custom.precedence: logging
                                  service.name: logging-service
                                  service.namespace: logging-namespace
                        """,
                MediaTypes.APPLICATION_YAML));

        var spanExporter = new CapturingSpanExporter();
        var metricExporter = new CapturingMetricExporter();
        var logExporter = new CapturingLogRecordExporter();
        var builder = OpenTelemetryConfig.builder().config(config.get("telemetry"));

        builder.tracing(OpenTelemetryTracingConfig.builder(builder.tracing().orElseThrow())
                                .addProcessor(SimpleSpanProcessor.create(spanExporter))
                                .build());
        builder.metrics(OpenTelemetryMetricsConfig.builder(builder.metrics().orElseThrow())
                                .addReader(PeriodicMetricReader.create(metricExporter))
                                .build());
        builder.logging(OpenTelemetryLoggingConfig.builder(builder.logging().orElseThrow())
                                .addProcessor(SimpleLogRecordProcessor.create(logExporter))
                                .build());

        var telemetryConfig = builder.buildPrototype();
        var resourceConfig = telemetryConfig.resource().orElseThrow();
        assertThat(resourceConfig.serviceNamespace().orElseThrow(), is("retail"));
        assertThat(resourceConfig.serviceInstanceId().orElseThrow(), is("checkout-7f3a"));
        assertThat(resourceConfig.deploymentEnvironmentName().orElseThrow(), is("production"));
        assertThat(resourceConfig.containerId().orElseThrow(), is("container-123"));
        assertThat(resourceConfig.containerImageName().orElseThrow(),
                   is("registry.example.com/retail/checkout"));
        assertThat(resourceConfig.containerImageTags(), is(List.of("latest", "2.1")));
        assertThat(resourceConfig.containerImageRepoDigests(),
                   is(List.of("checkout@sha256:first", "checkout@sha256:second")));

        try (var sdk = telemetryConfig.openTelemetrySdk()) {
            sdk.getTracer("resource-test").spanBuilder("test-span").startSpan().end();
            sdk.getMeter("resource-test").counterBuilder("test-counter").build().add(1);
            assertThat(sdk.getSdkMeterProvider().forceFlush().join(5, TimeUnit.SECONDS).isSuccess(), is(true));
            sdk.getLogsBridge().get("resource-test").logRecordBuilder().setBody("test-log").emit();

            assertResource(spanExporter.resource(), "tracing");
            assertResource(metricExporter.resource(), "metrics");
            assertResource(logExporter.resource(), "logging");
        }
    }

    @Test
    void testProgrammaticResourceAttributes() {
        var spanExporter = new CapturingSpanExporter();
        var resourceConfig = OpenTelemetryResourceConfig.builder()
                .serviceNamespace("programmatic-namespace")
                .serviceInstanceId("programmatic-instance")
                .deploymentEnvironmentName("programmatic-environment")
                .containerId("programmatic-container")
                .containerImageName("registry.example.com/programmatic")
                .containerImageTags(List.of("latest", "3.0"))
                .containerImageRepoDigests(List.of("programmatic@sha256:first", "programmatic@sha256:second"))
                .attributes(Attributes.builder()
                                    .put(CUSTOM_STRING, "programmatic-string")
                                    .put(CUSTOM_LONG, 7L)
                                    .put(CUSTOM_DOUBLE, 8.5)
                                    .put(CUSTOM_BOOLEAN, true))
                .build();
        var telemetryConfig = OpenTelemetryConfig.builder()
                .service("programmatic-service")
                .global(false)
                .resource(resourceConfig)
                .tracing(OpenTelemetryTracingConfig.builder()
                                 .addProcessor(SimpleSpanProcessor.create(spanExporter))
                                 .build())
                .buildPrototype();

        try (var sdk = telemetryConfig.openTelemetrySdk()) {
            sdk.getTracer("resource-test").spanBuilder("test-span").startSpan().end();
            var resource = spanExporter.resource();
            assertThat(resource.getAttribute(ServiceAttributes.SERVICE_NAME), is("programmatic-service"));
            assertThat(resource.getAttribute(ServiceAttributes.SERVICE_NAMESPACE), is("programmatic-namespace"));
            assertThat(resource.getAttribute(ServiceAttributes.SERVICE_INSTANCE_ID), is("programmatic-instance"));
            assertThat(resource.getAttribute(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME),
                       is("programmatic-environment"));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_ID), is("programmatic-container"));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_NAME),
                       is("registry.example.com/programmatic"));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_TAGS), is(List.of("latest", "3.0")));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_REPO_DIGESTS),
                       is(List.of("programmatic@sha256:first", "programmatic@sha256:second")));
            assertThat(resource.getAttribute(CUSTOM_STRING), is("programmatic-string"));
            assertThat(resource.getAttribute(CUSTOM_LONG), is(7L));
            assertThat(resource.getAttribute(CUSTOM_DOUBLE), is(8.5));
            assertThat(resource.getAttribute(CUSTOM_BOOLEAN), is(true));
        }
    }

    @Test
    void testOmittedResourceSettingsPreserveDefaults() {
        var spanExporter = new CapturingSpanExporter();
        var telemetryConfig = OpenTelemetryConfig.builder()
                .service("default-resource-service")
                .global(false)
                .tracing(OpenTelemetryTracingConfig.builder()
                                 .addProcessor(SimpleSpanProcessor.create(spanExporter))
                                 .build())
                .buildPrototype();

        try (var sdk = telemetryConfig.openTelemetrySdk()) {
            sdk.getTracer("resource-test").spanBuilder("test-span").startSpan().end();
            var resource = spanExporter.resource();
            var defaultResource = Resource.getDefault();
            assertThat(resource.getAttribute(ServiceAttributes.SERVICE_NAMESPACE),
                       is(defaultResource.getAttribute(ServiceAttributes.SERVICE_NAMESPACE)));
            assertThat(resource.getAttribute(ServiceAttributes.SERVICE_INSTANCE_ID),
                       is(defaultResource.getAttribute(ServiceAttributes.SERVICE_INSTANCE_ID)));
            assertThat(resource.getAttribute(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME),
                       is(defaultResource.getAttribute(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME)));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_ID),
                       is(defaultResource.getAttribute(ContainerAttributes.CONTAINER_ID)));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_NAME),
                       is(defaultResource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_NAME)));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_TAGS),
                       is(defaultResource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_TAGS)));
            assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_REPO_DIGESTS),
                       is(defaultResource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_REPO_DIGESTS)));
        }
    }

    @Test
    void testSuppliedOpenTelemetryTakesPrecedence() {
        var supplied = OpenTelemetry.noop();
        var telemetryConfig = OpenTelemetryConfig.builder()
                .service("ignored-service")
                .resource(OpenTelemetryResourceConfig.builder()
                                  .serviceNamespace("ignored-namespace")
                                  .build())
                .openTelemetry(supplied)
                .buildPrototype();

        assertThat(telemetryConfig.openTelemetry(), sameInstance(supplied));
        telemetryConfig.openTelemetrySdk().close();
    }

    private static void assertResource(Resource resource, String expectedPrecedence) {
        assertThat(resource, is(notNullValue()));
        assertThat(resource.getAttribute(ServiceAttributes.SERVICE_NAME), is("checkout"));
        assertThat(resource.getAttribute(ServiceAttributes.SERVICE_NAMESPACE), is("retail"));
        assertThat(resource.getAttribute(ServiceAttributes.SERVICE_INSTANCE_ID), is("checkout-7f3a"));
        assertThat(resource.getAttribute(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME), is("production"));
        assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_ID), is("container-123"));
        assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_NAME),
                   is("registry.example.com/retail/checkout"));
        assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_TAGS), is(List.of("latest", "2.1")));
        assertThat(resource.getAttribute(ContainerAttributes.CONTAINER_IMAGE_REPO_DIGESTS),
                   is(List.of("checkout@sha256:first", "checkout@sha256:second")));
        assertThat(resource.getAttribute(CUSTOM_STRING), is("common"));
        assertThat(resource.getAttribute(CUSTOM_LONG), is(3L));
        assertThat(resource.getAttribute(CUSTOM_DOUBLE), is(4.5));
        assertThat(resource.getAttribute(CUSTOM_BOOLEAN), is(true));
        assertThat(resource.getAttribute(PRECEDENCE), is(expectedPrecedence));
    }

    private static CompletableResultCode success() {
        return CompletableResultCode.ofSuccess();
    }

    private static final class CapturingSpanExporter implements SpanExporter {
        private final AtomicReference<Resource> resource = new AtomicReference<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            resource.set(spans.iterator().next().getResource());
            return success();
        }

        @Override
        public CompletableResultCode flush() {
            return success();
        }

        @Override
        public CompletableResultCode shutdown() {
            return success();
        }

        private Resource resource() {
            return resource.get();
        }
    }

    private static final class CapturingMetricExporter implements MetricExporter {
        private final AtomicReference<Resource> resource = new AtomicReference<>();

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public Aggregation getDefaultAggregation(InstrumentType instrumentType) {
            return Aggregation.defaultAggregation();
        }

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            resource.set(metrics.iterator().next().getResource());
            return success();
        }

        @Override
        public CompletableResultCode flush() {
            return success();
        }

        @Override
        public CompletableResultCode shutdown() {
            return success();
        }

        private Resource resource() {
            return resource.get();
        }
    }

    private static final class CapturingLogRecordExporter implements LogRecordExporter {
        private final AtomicReference<Resource> resource = new AtomicReference<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            resource.set(logs.iterator().next().getResource());
            return success();
        }

        @Override
        public CompletableResultCode flush() {
            return success();
        }

        @Override
        public CompletableResultCode shutdown() {
            return success();
        }

        private Resource resource() {
            return resource.get();
        }
    }
}

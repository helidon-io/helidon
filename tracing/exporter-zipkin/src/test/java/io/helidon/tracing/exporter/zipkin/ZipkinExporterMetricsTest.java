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

package io.helidon.tracing.exporter.zipkin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.InternalTelemetryVersion;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("removal")
class ZipkinExporterMetricsTest {
    @Test
    void preservesLegacyMetrics() {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        try (var meters = SdkMeterProvider.builder().registerMetricReader(reader).build();
             var exporter = ZipkinSpanExporter.builder().sender(new ZipkinSpanExporterTest.TestSender())
                     .meterProvider(meters).build()) {
            exporter.export(List.of(ZipkinTestSpans.span().build()));
            var data = reader.collectAllMetrics();
            var seen = data.stream().filter(metric -> metric.getName().equals("zipkin.exporter.seen")).findFirst().orElseThrow();
            assertThat(seen.getInstrumentationScopeInfo().getName(), is("io.opentelemetry.exporters.zipkin-http-json"));
            assertThat(seen.getLongSumData().getPoints().iterator().next().getValue(), is(1L));
            var exported = data.stream().filter(metric -> metric.getName().equals("zipkin.exporter.exported"))
                    .findFirst().orElseThrow().getLongSumData().getPoints().iterator().next();
            assertThat(exported.getValue(), is(1L));
            assertThat(exported.getAttributes().get(AttributeKey.booleanKey("success")), is(true));
            assertThat(exported.getAttributes().get(AttributeKey.stringKey("type")), is("span"));
        }
    }

    @Test
    void retriesNoopInstrumentsWhenProviderFacadeBecomesActive() {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        AtomicReference<MeterProvider> delegate = new AtomicReference<>(MeterProvider.noop());
        MeterProvider facade = new MeterProvider() {
            @Override
            public MeterBuilder meterBuilder(String scope) {
                return delegate.get().meterBuilder(scope);
            }
        };
        try (var meters = SdkMeterProvider.builder().registerMetricReader(reader).build();
             var exporter = ZipkinSpanExporter.builder().sender(new ZipkinSpanExporterTest.TestSender())
                     .meterProvider(facade).build()) {
            exporter.export(List.of(ZipkinTestSpans.span().build()));
            delegate.set(meters);
            exporter.export(List.of(ZipkinTestSpans.span().build()));
            var seen = reader.collectAllMetrics().stream().filter(metric -> metric.getName().equals("zipkin.exporter.seen"))
                    .findFirst().orElseThrow().getLongSumData().getPoints().iterator().next();
            assertThat(seen.getValue(), is(1L));
        }
    }

    @Test
    void preservesLatestMetricsOnFailure() {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        var sender = new ZipkinSpanExporterTest.TestSender() {
            @Override
            public void send(List<byte[]> spans) throws IOException {
                throw new IOException("sensitive endpoint");
            }
        };
        try (var meters = SdkMeterProvider.builder().registerMetricReader(reader).build();
             var exporter = ZipkinSpanExporter.builder().sender(sender).meterProvider(meters)
                     .internalTelemetryVersion(InternalTelemetryVersion.LATEST).build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(false));
            var data = reader.collectAllMetrics();
            var exported = data.stream().filter(metric -> metric.getName().equals("otel.sdk.exporter.span.exported"))
                    .findFirst().orElseThrow();
            var point = exported.getLongSumData().getPoints().iterator().next();
            assertThat(exported.getUnit(), is("{span}"));
            assertThat(point.getValue(), is(1L));
            assertThat(point.getAttributes().get(AttributeKey.stringKey("error.type")), is(IOException.class.getName()));
            assertThat(point.getAttributes().get(AttributeKey.stringKey("otel.component.type")), is("zipkin_http_span_exporter"));
            assertThat(point.getAttributes().get(AttributeKey.stringKey("server.address")), is("localhost"));
            assertThat(point.getAttributes().get(AttributeKey.longKey("server.port")), is(9411L));
            var inflight = data.stream().filter(metric -> metric.getName().equals("otel.sdk.exporter.span.inflight"))
                    .findFirst().orElseThrow().getLongSumData().getPoints().iterator().next();
            assertThat(inflight.getValue(), is(0L));
            assertThat(data.stream().anyMatch(metric -> metric.getName().equals("otel.sdk.exporter.operation.duration")), is(true));
        }
    }
}

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
/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.exporter.zipkin;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.InternalTelemetryVersion;
import zipkin2.reporter.Encoding;

// Metric names, attributes, units, and scopes follow OTel Zipkin 1.64.0's legacy and semconv metrics.
final class ZipkinExporterMetrics {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final Attributes SPAN_TYPE = Attributes.builder().put("type", "span").build();
    private static final String COMPONENT_TYPE = "zipkin_http_span_exporter";

    private final InternalTelemetryVersion version;
    private final Supplier<MeterProvider> supplier;
    private final String scope;
    private final URI endpoint;
    private final ReentrantLock lock = new ReentrantLock();

    private MeterProvider provider;
    private Instruments instruments;
    private Attributes attributes;

    ZipkinExporterMetrics(InternalTelemetryVersion version, Supplier<MeterProvider> supplier, Encoding encoding, URI endpoint) {
        this.version = version;
        this.supplier = supplier;
        this.endpoint = endpoint;
        scope = version == InternalTelemetryVersion.LEGACY
                ? "io.opentelemetry.exporters.zipkin-" + (encoding == Encoding.JSON ? "http-json" : "http")
                : "io.opentelemetry.exporters." + COMPONENT_TYPE;
    }

    Recording start(int count) {
        lock.lock();
        try {
            MeterProvider current = supplier.get();
            if (provider != current || instruments == null || instruments.noop()) {
                Meter meter = current.get(scope);
                if (version == InternalTelemetryVersion.LEGACY) {
                    instruments = new Instruments(meter.counterBuilder("zipkin.exporter.seen").build(),
                                                  meter.counterBuilder("zipkin.exporter.exported").build(), null, null);
                    attributes = SPAN_TYPE;
                } else {
                    if (attributes == null) {
                        var builder = Attributes.builder()
                                .put("otel.component.type", COMPONENT_TYPE)
                                .put("otel.component.name", COMPONENT_TYPE + "/" + NEXT_ID.getAndIncrement());
                        if (endpoint.getHost() != null) {
                            builder.put("server.address", endpoint.getHost());
                        }
                        builder.put("server.port", endpoint.getPort() == -1
                                ? ("https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80) : endpoint.getPort());
                        attributes = builder.build();
                    }
                    instruments = new Instruments(null,
                                                  meter.counterBuilder("otel.sdk.exporter.span.exported")
                                                          .setUnit("{span}")
                                                          .setDescription("The number of spans for which the export "
                                                                                  + "has finished, "
                                                                                  + "either successful or failed")
                                                          .build(),
                                                  meter.upDownCounterBuilder("otel.sdk.exporter.span.inflight")
                                                          .setUnit("{span}")
                                                          .setDescription("The number of spans which were passed "
                                                                                  + "to the exporter, "
                                                                                  + "but that have not been exported yet "
                                                                                  + "(neither successful, nor failed)")
                                                          .build(),
                                                  meter.histogramBuilder("otel.sdk.exporter.operation.duration")
                                                          .setUnit("s")
                                                          .setDescription("The duration of exporting a batch "
                                                                                  + "of telemetry records")
                                                          .setExplicitBucketBoundariesAdvice(List.of())
                                                          .build());
                }
                provider = current;
            }
            return new Recording(instruments, attributes, count);
        } finally {
            lock.unlock();
        }
    }

    static final class Recording {
        private final Instruments instruments;
        private final Attributes attributes;
        private final int count;
        private final long started = System.nanoTime();

        private Recording(Instruments instruments, Attributes attributes, int count) {
            this.instruments = instruments;
            this.attributes = attributes;
            this.count = count;
            if (instruments.seen != null) {
                instruments.seen.add(count, attributes);
            } else {
                instruments.inflight.add(count, attributes);
            }
        }

        void finish(String errorType) {
            var builder = attributes.toBuilder();
            if (instruments.seen != null) {
                builder.put("success", errorType == null);
            } else {
                instruments.inflight.add(-count, attributes);
                if (errorType != null) {
                    builder.put("error.type", errorType);
                }
                instruments.duration.record((System.nanoTime() - started) / 1_000_000_000.0, builder.build());
            }
            instruments.exported.add(count, builder.build());
        }
    }

    private static final class Instruments {
        private final LongCounter seen;
        private final LongCounter exported;
        private final LongUpDownCounter inflight;
        private final DoubleHistogram duration;

        private Instruments(LongCounter seen, LongCounter exported, LongUpDownCounter inflight, DoubleHistogram duration) {
            this.seen = seen;
            this.exported = exported;
            this.inflight = inflight;
            this.duration = duration;
        }

        private boolean noop() {
            // Match upstream: a stable provider facade can become active after initially returning no-op instruments.
            return exported.getClass().getSimpleName().startsWith("Noop");
        }
    }
}

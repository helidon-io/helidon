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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.config.ComponentTracingConfig;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * Real tracing-observer exchanges with an owned, non-exporting OpenTelemetry SDK.
 * Both request and trace-context events are enabled in the ENABLED recording mode.
 * Uses the same HTTP transport fixture as the separate core request-event benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class TracingRequestJfrJmhBenchmark {
    static final String TRACE_EVENT = "io.helidon.RequestTraceContext";
    static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    static final String PARENT_SPAN_ID = "0123456789abcdef";

    private static final TracingConfig INBOUND_ONLY = TracingConfig.builder()
            .addComponent(ComponentTracingConfig.builder("web-server")
                                  .addSpan(SpanTracingConfig.builder("HTTP Request").enabled(false).build())
                                  .build())
            .build();

    /** Transport, with clear-text HTTP/2 prior knowledge. */
    @Param({"HTTP1", "HTTP2"})
    public String protocol;

    /** No recording, both events disabled, or both events enabled. */
    @Param({"OFF", "DISABLED", "ENABLED"})
    public String recordingMode;

    /** Start a real root server span, or retain only the extracted inbound context. */
    @Param({"SERVER_SPAN", "INBOUND_CONTEXT"})
    public String tracingMode;

    private HttpRequestJfrJmhBenchmark transport;
    private OpenTelemetrySdk telemetry;

    @Setup
    public void setup() {
        setup(() -> { });
    }

    @Benchmark
    public String tracedKeepAliveExchange() {
        return transport.keepAliveExchange();
    }

    @TearDown
    public void tearDown() {
        try {
            if (transport != null) {
                transport.tearDown();
                transport = null;
            }
        } finally {
            if (telemetry != null) {
                telemetry.close();
                telemetry = null;
            }
        }
    }

    void setup(Runnable work) {
        if (!List.of("SERVER_SPAN", "INBOUND_CONTEXT").contains(tracingMode)) {
            throw new IllegalArgumentException("Unknown tracing mode: " + tracingMode);
        }
        try {
            telemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().build())
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            var tracer = HelidonOpenTelemetry.create(telemetry, telemetry.getTracer("jfr-benchmark"), Map.of());
            var observer = TracingObserver.builder()
                    .tracer(tracer)
                    .envConfig("SERVER_SPAN".equals(tracingMode) ? TracingConfig.ENABLED : INBOUND_ONLY)
                    .build();
            transport = new HttpRequestJfrJmhBenchmark();
            transport.protocol = protocol;
            transport.recordingMode = recordingMode;
            transport.setup(work,
                            builder -> builder.addFeature(ObserveFeature.builder()
                                                                  .observersDiscoverServices(false)
                                                                  .addObserver(observer)
                                                                  .build()),
                            "SERVER_SPAN".equals(tracingMode)
                                    ? Map.of()
                                    : Map.of("traceparent", "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01"),
                            List.of(HttpRequestJfrJmhBenchmark.REQUEST_EVENT, TRACE_EVENT));
        } catch (RuntimeException | Error failure) {
            try {
                tearDown();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    void dump(Path destination) throws IOException {
        transport.dump(destination);
    }
}

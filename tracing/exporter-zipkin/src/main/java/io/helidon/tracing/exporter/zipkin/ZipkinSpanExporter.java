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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import zipkin2.Span;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.BytesMessageSender;

/**
 * Transitional OpenTelemetry exporter sending Zipkin spans over HTTP using Helidon WebClient.
 * Export is synchronous; configure an OpenTelemetry batch span processor for asynchronous batching.
 *
 * @deprecated Migrate to an OTLP exporter.
 */
@Deprecated(since = "4.5.7", forRemoval = true)
@SuppressWarnings("removal")
public final class ZipkinSpanExporter implements SpanExporter, RuntimeType.Api<ZipkinSpanExporterConfig> {
    private static final System.Logger LOGGER = System.getLogger(ZipkinSpanExporter.class.getName());
    private static final long LOG_INTERVAL = TimeUnit.SECONDS.toNanos(60);

    private final ZipkinSpanExporterConfig config;
    private final BytesMessageSender sender;
    private final BytesEncoder<Span> encoder;
    private final OtelToZipkinSpanTransformer transformer;
    private final ZipkinExporterMetrics metrics;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final AtomicLong lastFailureLog = new AtomicLong();

    // All lifecycle fields are guarded by lock. DRAINING permanently closes admission.
    private State state = State.ACTIVE;
    private int activeExports;
    private boolean closing;

    private ZipkinSpanExporter(ZipkinSpanExporterConfig config) {
        this.config = config;
        encoder = config.encoder().orElse(config.encoding());
        transformer = OtelToZipkinSpanTransformer.create(
                config.localIpAddressSupplier().orElseGet(LocalInetAddressSupplier::getInstance));
        // Initialize everything that can fail before acquiring sender ownership.
        metrics = new ZipkinExporterMetrics(config.internalTelemetryVersion(),
                                           () -> config.meterProvider().orElseGet(
                                                   () -> GlobalOpenTelemetry.getOrNoop().getMeterProvider()),
                                           config.sender().map(BytesMessageSender::encoding).orElse(encoder.encoding()),
                                           URI.create(config.endpoint()));
        sender = config.sender().orElseGet(() -> new WebClientSender(config));
    }

    /**
     * Create an exporter builder.
     *
     * @return builder
     */
    public static ZipkinSpanExporterConfig.Builder builder() {
        return ZipkinSpanExporterConfig.builder();
    }

    /**
     * Create an exporter using defaults.
     *
     * @return exporter
     */
    public static ZipkinSpanExporter create() {
        return builder().build();
    }

    /**
     * Create an exporter from its configuration node.
     *
     * @param config exporter configuration node
     * @return exporter
     */
    public static ZipkinSpanExporter create(Config config) {
        return builder().config(Objects.requireNonNull(config, "config")).build();
    }

    /**
     * Create an exporter from a configuration prototype.
     *
     * @param config exporter configuration
     * @return exporter
     */
    public static ZipkinSpanExporter create(ZipkinSpanExporterConfig config) {
        return new ZipkinSpanExporter(Objects.requireNonNull(config, "config"));
    }

    /**
     * Create an exporter customizing its configuration.
     *
     * @param consumer configuration customizer
     * @return exporter
     */
    public static ZipkinSpanExporter create(Consumer<ZipkinSpanExporterConfig.Builder> consumer) {
        return builder().update(Objects.requireNonNull(consumer, "consumer")).build();
    }

    @Override
    public ZipkinSpanExporterConfig prototype() {
        return config;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        Objects.requireNonNull(spans, "spans");
        lock.lock();
        try {
            if (state != State.ACTIVE) {
                return CompletableResultCode.ofFailure();
            }
            activeExports++;
        } finally {
            lock.unlock();
        }

        try {
            var recording = metrics.start(spans.size());
            try {
                var encoded = new ArrayList<byte[]>(spans.size());
                for (SpanData span : spans) {
                    encoded.add(encoder.encode(transformer.generateSpan(span)));
                }
                CompletableResultCode result = new CompletableResultCode();
                Runnable send = () -> {
                    try {
                        sender.send(encoded);
                        recording.finish(null);
                        result.succeed();
                    } catch (IOException | RuntimeException e) {
                        recording.finish(e.getClass().getName());
                        logFailure();
                        result.fail();
                    }
                };
                if (InstrumentationUtil.shouldSuppressInstrumentation(Context.current())) {
                    send.run();
                } else {
                    InstrumentationUtil.suppressInstrumentation(send);
                }
                return result;
            } catch (RuntimeException e) {
                recording.finish(e.getClass().getName());
                logFailure();
                return CompletableResultCode.ofFailure();
            }
        } finally {
            lock.lock();
            try {
                activeExports--;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        lock.lock();
        try {
            if (state == State.CLOSED) {
                return CompletableResultCode.ofSuccess();
            }
            state = State.DRAINING;
            while (activeExports != 0 || closing) {
                try {
                    changed.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return CompletableResultCode.ofFailure();
                }
                if (state == State.CLOSED) {
                    return CompletableResultCode.ofSuccess();
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                return CompletableResultCode.ofFailure();
            }
            closing = true;
        } finally {
            lock.unlock();
        }

        boolean success = false;
        try {
            sender.close();
            success = true;
            return CompletableResultCode.ofSuccess();
        } catch (IOException | RuntimeException e) {
            if (e instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            // Third-party exception messages and causes can contain endpoints or span values.
            LOGGER.log(System.Logger.Level.WARNING, "Zipkin sender shutdown failed; cleanup can be retried");
            return CompletableResultCode.ofFailure();
        } finally {
            lock.lock();
            try {
                if (success) {
                    state = State.CLOSED;
                }
                closing = false;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public String toString() {
        return "ZipkinSpanExporter{transport=" + (config.sender().isPresent() ? "custom" : "helidon-webclient") + "}";
    }

    private void logFailure() {
        long now = System.nanoTime();
        long previous = lastFailureLog.get();
        if ((previous == 0 || now - previous >= LOG_INTERVAL) && lastFailureLog.compareAndSet(previous, now)) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to export Zipkin spans");
        }
    }

    private enum State {
        ACTIVE,
        DRAINING,
        CLOSED
    }
}

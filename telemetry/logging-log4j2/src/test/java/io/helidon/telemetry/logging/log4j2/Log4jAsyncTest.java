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
package io.helidon.telemetry.logging.log4j2;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.testing.junit5.MatcherWithRetry;
import io.helidon.telemetry.logging.LogBridge;
import io.helidon.telemetry.logging.LogBridgeContext;
import io.helidon.telemetry.logging.sdk.LogRecordExporters;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.async.AsyncLoggerContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class Log4jAsyncTest {
    private static LogBridge startBridge(BridgeTestSupport support) {
        var bridge = new Log4jLogBridgeProvider().create(support);
        try {
            bridge.start();
            return bridge;
        } catch (RuntimeException | Error failure) {
            bridge.close();
            throw failure;
        }
    }

    @Test
    void exporterLoggingCannotFeedBackThroughAsynchronousDelivery() throws Exception {
        var firstExport = new CountDownLatch(1);
        var barrier = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var exporter = LogRecordExporters.suppressLogging(new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> records) {
                for (var record : records) {
                    if (record.getBodyValue().asString().equals("barrier")) {
                        barrier.countDown();
                    } else {
                        if (attempts.incrementAndGet() < 4) {
                            LogManager.getLogger("custom-exporter").error("feedback");
                        }
                        firstExport.countDown();
                    }
                }
                return CompletableResultCode.ofSuccess();
            }
            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }
            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        });
        try (var sdk = OpenTelemetrySdk.builder().setLoggerProvider(SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter)).build()).build()) {
            var context = new LogBridgeContext(sdk) {
                @Override
                protected void runIfActive(Runnable operation) {
                    operation.run();
                }
            };
            var bridge = new Log4jLogBridgeProvider().create(context);
            try {
                bridge.start();
                LogManager.getLogger("custom-exporter").error("initial");
                assertThat(firstExport.await(5, TimeUnit.SECONDS), is(true));
                LogManager.getLogger("custom-exporter").error("barrier");
                assertThat(barrier.await(5, TimeUnit.SECONDS), is(true));
                assertThat(attempts.get(), is(1));
            } finally {
                bridge.stop();
                bridge.close();
            }
        }
    }

    @Test
    void asynchronousDeliveryUsesTheProducerContextAndEmitsOnce() {
        assertThat(LogManager.getContext(false) instanceof AsyncLoggerContext, is(true));
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            var parent = SpanContext.create("11111111111111111111111111111111", "2222222222222222",
                                            TraceFlags.getSampled(), TraceState.builder().put("vendor", "producer-state").build());
            try (var _ = Span.wrap(parent).makeCurrent()) {
                LogManager.getLogger("async-application").error("async-record");
            }
            MatcherWithRetry.assertThatWithRetry("One asynchronous record", () -> support.records.size(), is(1), 50, 100);
            assertThat(support.records.getFirst().getSpanContext(), is(parent));
        }
    }
}

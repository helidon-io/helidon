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
package io.helidon.telemetry.logging.jul;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import io.helidon.telemetry.logging.LogBridge;
import io.helidon.telemetry.logging.LogBridgeContext;
import io.helidon.telemetry.logging.sdk.LogRecordExporters;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JulTelemetryTest {
    private static LogBridge startBridge(BridgeTestSupport support) {
        var bridge = new JulLogBridgeProvider().create(support);
        try {
            bridge.start();
            return bridge;
        } catch (RuntimeException | Error failure) {
            bridge.close();
            throw failure;
        }
    }

    @Test
    void declinedPublicationDoesNotAccessSdk() {
        var calls = new AtomicInteger();
        var context = new DecliningLogContext(calls);
        try (var bridge = new JulLogBridgeProvider().create(context)) {
            bridge.start();
            int before = calls.get();
            Logger.getLogger("declined").warning("after-stop");
            assertThat(calls.get(), is(before));
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
                            Logger.getLogger("custom-exporter").warning("feedback");
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
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).setScheduleDelay(Duration.ofMillis(10)).build()).build()).build()) {
            var context = new LogBridgeContext(sdk) {
                @Override
                protected void runIfActive(Runnable operation) {
                    operation.run();
                }
            };
            var bridge = new JulLogBridgeProvider().create(context);
            try {
                bridge.start();
                Logger.getLogger("custom-exporter").warning("initial");
                assertThat(firstExport.await(5, TimeUnit.SECONDS), is(true));
                Logger.getLogger("custom-exporter").warning("barrier");
                assertThat(barrier.await(5, TimeUnit.SECONDS), is(true));
                assertThat(attempts.get(), is(1));
            } finally {
                bridge.stop();
                bridge.close();
            }
        }
    }

    @Test
    void creationDoesNotAttachHooksAndClosingBeforeStartPreventsReopening() {
        try (var support = new BridgeTestSupport()) {
            var bridge = new JulLogBridgeProvider().create(support);
            Logger.getLogger("application").warning("before-start");
            assertThat(support.records.isEmpty(), is(true));
            bridge.stop();
            bridge.close();
            bridge.close();
            assertThrows(IllegalStateException.class, bridge::start);
            Logger.getLogger("application").warning("after-close");
            assertThat(support.records.isEmpty(), is(true));
        }
    }

    @Test
    void preservesSeverityTimestampScopeLoggerNameAndFormatting() {
        try (var support = new BridgeTestSupport()) {
            var handler = new TelemetryLogHandler(support);
            var levels = List.of(Level.FINEST, Level.FINER, Level.FINE, Level.CONFIG, Level.INFO, Level.WARNING, Level.SEVERE);
            var severities = List.of(Severity.TRACE, Severity.TRACE, Severity.DEBUG, Severity.DEBUG,
                                     Severity.INFO, Severity.WARN, Severity.ERROR);
            var timestamp = Instant.parse("2026-01-02T03:04:05.123456789Z");
            for (int i = 0; i < levels.size(); i++) {
                var record = new LogRecord(levels.get(i), "message {0}");
                record.setParameters(new Object[] {i});
                record.setLoggerName("jul-test-" + i);
                record.setInstant(timestamp);
                handler.publish(record);
            }
            assertThat(support.records.size(), is(7));
            for (int i = 0; i < levels.size(); i++) {
                var record = support.records.get(i);
                assertThat(record.getBodyValue().asString(), is("message " + i));
                assertThat(record.getSeverity(), is(severities.get(i)));
                assertThat(record.getSeverityText(), is(levels.get(i).getName()));
                assertThat(record.getInstrumentationScopeInfo().getName(), is("io.helidon.telemetry.logging"));
                assertThat(record.getAttributes().get(AttributeKey.stringKey("log.logger")), is("jul-test-" + i));
                assertThat(record.getTimestampEpochNanos(), is(timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano()));
            }
        }
    }

    @Test
    void disabledPipelineDoesNotFormatParameters() {
        try (var support = new BridgeTestSupport(false)) {
            var formatted = new AtomicInteger();
            var record = new LogRecord(Level.INFO, "{0}");
            record.setParameters(new Object[] {new Object() {
                @Override
                public String toString() {
                    formatted.incrementAndGet();
                    return "parameter";
                }
            }});
            new TelemetryLogHandler(support).publish(record);
            assertThat(formatted.get(), is(0));
        }
    }

    @Test
    void publicationFailureDoesNotPreventLaterRecords() {
        try (var support = new BridgeTestSupport()) {
            var handler = new TelemetryLogHandler(support);
            var broken = new LogRecord(Level.INFO, "broken") {
                @Override
                public Instant getInstant() {
                    throw new IllegalStateException("Synthetic record failure");
                }
            };
            assertThrows(IllegalStateException.class, () -> handler.publish(broken));
            handler.publish(new LogRecord(Level.INFO, "after-failure"));
            assertThat(support.records.size(), is(1));
            assertThat(support.records.getFirst().getBodyValue().asString(), is("after-failure"));
        }
    }

    @Test
    void concurrentRecordsKeepTheirProducingContextAndExceptions() throws Exception {
        try (var support = new BridgeTestSupport(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = new TelemetryLogHandler(support);
            var results = IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
                var parent = SpanContext.create(String.format("%032x", index + 1), String.format("%016x", index + 1),
                                                TraceFlags.getSampled(), TraceState.getDefault());
                try (var _ = Span.wrap(parent).makeCurrent()) {
                    var record = new LogRecord(Level.WARNING, "parallel-" + index);
                    record.setThrown(new IllegalStateException("synthetic"));
                    handler.publish(record);
                }
                return parent.getTraceId();
            })).toList();
            for (int i = 0; i < results.size(); i++) {
                String body = "parallel-" + i;
                String trace = results.get(i).get(5, TimeUnit.SECONDS);
                var record = support.records.stream().filter(log -> body.equals(log.getBodyValue().asString())).findFirst().orElseThrow();
                assertThat(record.getSpanContext().getTraceId(), is(trace));
                assertThat(record.getAttributes().get(AttributeKey.stringKey("exception.type")),
                           is(IllegalStateException.class.getName()));
            }
        }
    }

    @Test
    void detachesOnlyOwnedHandlerAndDoesNotCloseBorrowedSdk() {
        var root = Logger.getLogger("");
        var other = new Handler() {
            @Override
            public void publish(LogRecord record) {
            }
            @Override
            public void flush() {
            }
            @Override
            public void close() {
            }
        };
        root.addHandler(other);
        int before = root.getHandlers().length;
        try (var support = new BridgeTestSupport()) {
            var bridge = startBridge(support);
            assertThat(root.getHandlers().length, is(before + 1));
            bridge.stop();
            bridge.close();
            bridge.close();
            assertThat(root.getHandlers().length, is(before));
            support.telemetry().getLogsBridge().get("borrowed").logRecordBuilder().setBody("still-open").emit();
            assertThat(support.records.getFirst().getBodyValue().asString(), is("still-open"));
        } finally {
            root.removeHandler(other);
        }
    }

    @Test
    void systemLoggerIsCapturedOnceAndHandlerSurvivesReconfiguration() throws Exception {
        var root = Logger.getLogger("");
        var previous = root.getHandlers();
        var level = root.getLevel();
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(".level=ALL\n".getBytes(StandardCharsets.UTF_8)));
            System.getLogger("system-test").log(System.Logger.Level.WARNING, "system-record");
            assertThat(support.records.stream().filter(log -> "system-record".equals(log.getBodyValue().asString())).count(), is(1L));
        } finally {
            for (var handler : previous) {
                root.addHandler(handler);
            }
            root.setLevel(level);
        }
    }
}

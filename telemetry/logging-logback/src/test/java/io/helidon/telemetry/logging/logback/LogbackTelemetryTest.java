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
package io.helidon.telemetry.logging.logback;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.telemetry.logging.LogBridge;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogbackTelemetryTest {
    private static LogBridge startBridge(BridgeTestSupport support) {
        var bridge = new LogbackLogBridgeProvider().create(support);
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
        try (var bridge = new LogbackLogBridgeProvider().create(context)) {
            bridge.start();
            int before = calls.get();
            LoggerFactory.getLogger("declined").error("after-stop");
            assertThat(calls.get(), is(before));
        }
    }

    @Test
    void unnamedEventsUseRootScope() {
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            var event = new LoggingEvent(getClass().getName(), logging.getLogger("fixture"),
                                         Level.WARN, "unnamed", null, null) {
                @Override
                public String getLoggerName() {
                    return null;
                }
            };
            var appenders = logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
            while (appenders.hasNext()) {
                var appender = appenders.next();
                if (appender.getName().startsWith("helidon-otel-logging-")) {
                    appender.doAppend(event);
                }
            }
            assertThat(support.records.size(), is(1));
            assertThat(support.records.getFirst().getInstrumentationScopeInfo().getName(), is("ROOT"));
        }
    }

    @Test
    void disabledPipelineDoesNotFormatParameters() {
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        try (var support = new BridgeTestSupport(false); var bridge = startBridge(support)) {
            var calls = new AtomicInteger();
            var event = new LoggingEvent();
            event.setLoggerName("disabled");
            event.setLevel(Level.WARN);
            event.setMessage("message {}");
            event.setArgumentArray(new Object[] {new Object() {
                @Override
                public String toString() { calls.incrementAndGet(); return "formatted"; }
            }});
            var appenders = logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
            while (appenders.hasNext()) {
                var appender = appenders.next();
                if (appender.getName().startsWith("helidon-otel-logging-")) {
                    appender.doAppend(event);
                }
            }
            assertThat(calls.get(), is(0));
        }
    }

    @Test
    void creationDoesNotAttachHooksAndClosingBeforeStartPreventsReopening() {
        try (var support = new BridgeTestSupport()) {
            var bridge = new LogbackLogBridgeProvider().create(support);
            LoggerFactory.getLogger("application").warn("before-start");
            assertThat(support.records.isEmpty(), is(true));
            bridge.stop();
            bridge.close();
            bridge.close();
            assertThrows(IllegalStateException.class, bridge::start);
            LoggerFactory.getLogger("application").warn("after-close");
            assertThat(support.records.isEmpty(), is(true));
        }
    }

    @Test
    void capturesNativeAndSystemLoggingOnceWithCorrelationAndExceptions() {
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            var parent = SpanContext.create("11111111111111111111111111111111", "2222222222222222",
                                            TraceFlags.getSampled(), TraceState.getDefault());
            MDC.put("not-opted-in", "private-test-value");
            try (var _ = Span.wrap(parent).makeCurrent()) {
                LoggerFactory.getLogger("application").warn("message {}", "parameter", new IllegalStateException("synthetic"));
                System.getLogger("system").log(System.Logger.Level.WARNING, "system-record");
            } finally {
                MDC.remove("not-opted-in");
            }
            var records = support.records.stream().filter(log -> log.getBodyValue().asString().startsWith("message")
                    || log.getBodyValue().asString().equals("system-record")).toList();
            assertThat(records.size(), is(2));
            for (var record : records) {
                assertThat(record.getSeverity(), is(Severity.WARN));
                assertThat(record.getSpanContext(), is(parent));
                assertThat(record.getAttributes().get(AttributeKey.stringKey("not-opted-in")), nullValue());
            }
            var nativeRecord = records.stream().filter(log -> log.getBodyValue().asString().equals("message parameter"))
                    .findFirst().orElseThrow();
            assertThat(nativeRecord.getAttributes().get(AttributeKey.stringKey("exception.type")),
                       is(IllegalStateException.class.getName()));
        }
    }

    @Test
    void stoppingWaitsForNativeConfigurationInsteadOfMutatingItsListenerIteration() throws Exception {
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocker = new Listener() {
            @Override
            public void onReset(LoggerContext context) {
                entered.countDown();
                await(release);
            }
        };
        var tail = new Listener() { };
        logging.addListener(blocker);
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            logging.addListener(tail);
            var reset = CompletableFuture.runAsync(() -> {
                var configuration = logging.getConfigurationLock();
                configuration.lock();
                try {
                    logging.reset();
                } finally {
                    configuration.unlock();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            var stopping = new CountDownLatch(1);
            var stopped = CompletableFuture.runAsync(() -> {
                stopping.countDown();
                bridge.stop();
            });
            try {
                assertThat(stopping.await(5, TimeUnit.SECONDS), is(true));
                assertThrows(TimeoutException.class, () -> stopped.get(1, TimeUnit.SECONDS));
                release.countDown();
                reset.get(5, TimeUnit.SECONDS);
                stopped.get(5, TimeUnit.SECONDS);
                assertThat(bridge.active(), is(false));
            } finally {
                release.countDown();
                reset.get(5, TimeUnit.SECONDS);
                stopped.get(5, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            logging.removeListener(blocker);
            logging.removeListener(tail);
        }
    }

    @Test
    void backendStopTerminatesRegistrationAndNewBridgeRecoversAfterRestart() {
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            assertThat(bridge.active(), is(true));
            logging.stop();
            assertThat(bridge.active(), is(false));
            logging.start();
            assertThat(bridge.active(), is(false));
            assertThrows(IllegalStateException.class, bridge::start);
            bridge.close();
            try (var replacement = startBridge(support)) {
                LoggerFactory.getLogger("restart").warn("after-restart");
                assertThat(replacement.active(), is(true));
                assertThat(support.records.stream().filter(record -> record.getBodyValue().asString().equals("after-restart")).count(),
                           is(1L));
            }
        }
    }

    @Test
    void survivesContextResetAndReleasesOnlyOwnedHooks() {
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        int listeners = logging.getCopyOfListenerList().size();
        try (var support = new BridgeTestSupport()) {
            var bridge = startBridge(support);
            logging.reset();
            LoggerFactory.getLogger("application").warn("after-reset");
            assertThat(support.records.stream().filter(log -> log.getBodyValue().asString().equals("after-reset")).count(), is(1L));
            bridge.stop();
            bridge.close();
            bridge.close();
            assertThat(logging.getCopyOfListenerList().size(), is(listeners));
            LoggerFactory.getLogger("application").warn("after-close");
            assertThat(support.records.stream().anyMatch(log -> log.getBodyValue().asString().equals("after-close")), is(false));
            support.telemetry().getLogsBridge().get("borrowed").logRecordBuilder().setBody("still-open").emit();
            assertThat(support.records.stream().anyMatch(log -> log.getBodyValue().asString().equals("still-open")), is(true));
        }
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for configuration");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private abstract static class Listener implements LoggerContextListener {
        @Override
        public boolean isResetResistant() {
            return true;
        }
        @Override
        public void onStart(LoggerContext context) { }
        @Override
        public void onReset(LoggerContext context) { }
        @Override
        public void onStop(LoggerContext context) { }
        @Override
        public void onLevelChange(Logger logger, Level level) { }
    }

}

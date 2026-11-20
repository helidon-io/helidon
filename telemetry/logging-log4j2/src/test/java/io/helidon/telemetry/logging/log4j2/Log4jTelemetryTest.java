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

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.telemetry.logging.LogBridge;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.NullAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Log4jTelemetryTest {
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
    void declinedPublicationDoesNotAccessSdk() {
        var calls = new AtomicInteger();
        var context = new DecliningLogContext(calls);
        try (var bridge = new Log4jLogBridgeProvider().create(context)) {
            bridge.start();
            int before = calls.get();
            LogManager.getLogger("declined").error("after-stop");
            assertThat(calls.get(), is(before));
        }
    }

    @Test
    void creationDoesNotAttachHooksAndClosingBeforeStartPreventsReopening() {
        try (var support = new BridgeTestSupport()) {
            var bridge = new Log4jLogBridgeProvider().create(support);
            LogManager.getLogger("application").error("before-start");
            assertThat(support.records.isEmpty(), is(true));
            bridge.stop();
            bridge.close();
            bridge.close();
            assertThrows(IllegalStateException.class, bridge::start);
            LogManager.getLogger("application").error("after-close");
            assertThat(support.records.isEmpty(), is(true));
        }
    }

    @Test
    void capturesNativeAndSystemLoggingOnceWithCorrelationAndFatalSeverity() {
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            var parent = SpanContext.create("11111111111111111111111111111111", "2222222222222222",
                                            TraceFlags.getSampled(), TraceState.getDefault());
            ThreadContext.put("not-opted-in", "private-test-value");
            try (var _ = Span.wrap(parent).makeCurrent()) {
                LogManager.getLogger("application").fatal("message {}", "parameter", new IllegalStateException("synthetic"));
                System.getLogger("system").log(System.Logger.Level.ERROR, "system-record");
            } finally {
                ThreadContext.remove("not-opted-in");
            }
            var records = support.records.stream().filter(log -> log.getBodyValue().asString().startsWith("message")
                    || log.getBodyValue().asString().equals("system-record")).toList();
            assertThat(records.size(), is(2));
            for (var record : records) {
                assertThat(record.getSpanContext(), is(parent));
                assertThat(record.getAttributes().get(AttributeKey.stringKey("not-opted-in")), nullValue());
            }
            var nativeRecord = records.stream().filter(log -> log.getBodyValue().asString().equals("message parameter"))
                    .findFirst().orElseThrow();
            assertThat(nativeRecord.getSeverity(), is(Severity.FATAL));
            assertThat(nativeRecord.getAttributes().get(AttributeKey.stringKey("exception.type")),
                       is(IllegalStateException.class.getName()));
        }
    }

    @Test
    void cleanupPreservesApplicationReplacementOfOwnedAppender() {
        var logging = (LoggerContext) LogManager.getContext(false);
        var configuration = logging.getConfiguration();
        var root = configuration.getRootLogger();
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            String name = root.getAppenders().keySet().stream().filter(key -> key.startsWith("helidon-otel-logging-"))
                    .findFirst().orElseThrow();
            var replacement = NullAppender.createAppender(name);
            replacement.start();
            root.removeAppender(name);
            configuration.getAppenders().put(name, replacement);
            root.addAppender(replacement, null, null);
            try {
                bridge.stop();
                bridge.close();
                assertThat(root.getAppenders().get(name), sameInstance(replacement));
                assertThat(configuration.getAppenders().get(name), sameInstance(replacement));
            } finally {
                root.removeAppender(name);
                configuration.getAppenders().remove(name, replacement);
                replacement.stop();
                logging.updateLoggers();
            }
        }
    }

    @Test
    void unnamedEventsUseRootScope() {
        try (var support = new BridgeTestSupport(); var bridge = startBridge(support)) {
            var logging = (LoggerContext) LogManager.getContext(false);
            var appender = logging.getConfiguration().getRootLogger().getAppenders().values().stream()
                    .filter(candidate -> candidate.getName().startsWith("helidon-otel-logging-")).findFirst().orElseThrow();
            appender.append(Log4jLogEvent.newBuilder().setLevel(Level.ERROR).setMessage(new SimpleMessage("unnamed")).build());
            assertThat(support.records.size(), is(1));
            assertThat(support.records.getFirst().getInstrumentationScopeInfo().getName(), is("ROOT"));
        }
    }

    @Test
    void disabledPipelineDoesNotFormatMessages() {
        try (var support = new BridgeTestSupport(false); var bridge = startBridge(support)) {
            var calls = new AtomicInteger();
            Message message = new Message() {
                @Override
                public String getFormattedMessage() {
                    calls.incrementAndGet();
                    return "formatted";
                }
                @Override
                public String getFormat() {
                    return "formatted";
                }
                @Override
                public Object[] getParameters() {
                    return new Object[0];
                }
                @Override
                public Throwable getThrowable() {
                    return null;
                }
            };
            var logging = (LoggerContext) LogManager.getContext(false);
            var appender = logging.getConfiguration().getRootLogger().getAppenders().values().stream()
                    .filter(candidate -> candidate.getName().startsWith("helidon-otel-logging-")).findFirst().orElseThrow();
            appender.append(Log4jLogEvent.newBuilder().setLoggerName("disabled").setLevel(Level.ERROR).setMessage(message).build());
            assertThat(calls.get(), is(0));
        }
    }

    @Test
    void survivesReconfigurationAndDoesNotCloseBorrowedSdk() {
        var logging = (LoggerContext) LogManager.getContext(false);
        try (var support = new BridgeTestSupport()) {
            var bridge = startBridge(support);
            logging.setConfiguration(new DefaultConfiguration());
            LogManager.getLogger("application").error("after-reconfiguration");
            assertThat(support.records.stream().filter(log -> log.getBodyValue().asString().equals("after-reconfiguration")).count(),
                       is(1L));
            bridge.stop();
            bridge.close();
            bridge.close();
            LogManager.getLogger("application").error("after-close");
            assertThat(support.records.stream().anyMatch(log -> log.getBodyValue().asString().equals("after-close")), is(false));
            support.telemetry().getLogsBridge().get("borrowed").logRecordBuilder().setBody("still-open").emit();
            assertThat(support.records.stream().anyMatch(log -> log.getBodyValue().asString().equals("still-open")), is(true));
        }
    }
}

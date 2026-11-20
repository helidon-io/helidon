/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import io.helidon.telemetry.logging.LogBridgeContext;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;

/**
 * Bridges JUL records without forwarding OpenTelemetry's own exporter diagnostics back to its SDK.
 */
final class TelemetryLogHandler extends Handler {
    private static final AttributeKey<String> LOGGER_NAME = AttributeKey.stringKey("log.logger");

    private final LogBridgeContext context;
    private final Logger logger;
    private final SimpleFormatter formatter = new SimpleFormatter();

    TelemetryLogHandler(LogBridgeContext context) {
        this.context = context;
        this.logger = context.telemetry().getLogsBridge().loggerBuilder("io.helidon.telemetry.logging").build();
        setLevel(Level.ALL);
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)
                || (record.getLoggerName() != null && record.getLoggerName().startsWith("io.opentelemetry"))) {
            return;
        }
        Severity severity = severity(record.getLevel());
        context.publish(record.getLoggerName() == null ? "" : record.getLoggerName(), () -> {
            if (logger.isEnabled(severity)) {
                emit(record, severity);
            }
        });
    }

    @Override
    public void flush() {
        // The SDK owns flushing and export scheduling.
    }

    @Override
    public void close() {
        // The caller detaches this handler before shutting down the SDK.
    }

    private static Severity severity(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            return Severity.ERROR;
        }
        if (value >= Level.WARNING.intValue()) {
            return Severity.WARN;
        }
        if (value >= Level.INFO.intValue()) {
            return Severity.INFO;
        }
        return value >= Level.FINE.intValue() ? Severity.DEBUG : Severity.TRACE;
    }

    private void emit(LogRecord record, Severity severity) {
        var builder = logger.logRecordBuilder()
                .setTimestamp(record.getInstant())
                .setObservedTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .setSeverity(severity)
                .setSeverityText(record.getLevel().getName())
                .setBody(formatter.formatMessage(record));
        if (record.getLoggerName() != null) {
            builder.setAttribute(LOGGER_NAME, record.getLoggerName());
        }
        if (record.getThrown() != null) {
            builder.setException(record.getThrown());
        }
        builder.emit();
    }
}

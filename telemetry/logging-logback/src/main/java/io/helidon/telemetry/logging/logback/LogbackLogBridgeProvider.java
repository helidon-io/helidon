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

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.logging.common.spi.LoggingProvider;
import io.helidon.logging.slf4j.Slf4jProvider;
import io.helidon.telemetry.logging.LogBridge;
import io.helidon.telemetry.logging.LogBridgeContext;
import io.helidon.telemetry.logging.spi.LogBridgeProvider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;

/**
 * Creates a deployment-owned Logback appender using the selected SDK.
 */
@Api.Internal
public final class LogbackLogBridgeProvider implements LogBridgeProvider {
    /**
     * Service-loader constructor.
     */
    public LogbackLogBridgeProvider() {
    }

    @Override
    public boolean supports(LoggingProvider provider) {
        return Objects.requireNonNull(provider, "provider") instanceof Slf4jProvider
                && LoggerFactory.getILoggerFactory() instanceof LoggerContext;
    }

    @Override
    public LogBridge create(LogBridgeContext context) {
        Objects.requireNonNull(context, "context");
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext logging)) {
            throw new IllegalStateException("Logback logging adapter requires an active Logback logger context");
        }
        return new Registration(logging, context);
    }

    private static final class Registration implements LogBridge, LoggerContextListener {
        private final ReentrantLock lock;
        private final LoggerContext logging;
        private final Logger root;
        private final OpenTelemetryAppender delegate = new OpenTelemetryAppender();
        private final UnsynchronizedAppenderBase<ILoggingEvent> appender;
        private final ConcurrentMap<String, io.opentelemetry.api.logs.Logger> loggers = new ConcurrentHashMap<>();
        private State state = State.NEW;

        private Registration(LoggerContext logging, LogBridgeContext bridgeContext) {
            this.logging = logging;
            lock = logging.getConfigurationLock();
            root = logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            delegate.setContext(logging);
            delegate.setOpenTelemetry(bridgeContext.telemetry());
            appender = new UnsynchronizedAppenderBase<>() {
                @Override
                protected void append(ILoggingEvent event) {
                    String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
                    bridgeContext.publish(loggerName, () -> {
                        var logger = loggers.computeIfAbsent(loggerName.isEmpty() ? "ROOT" : loggerName,
                                                            bridgeContext.telemetry().getLogsBridge()::get);
                        if (logger.isEnabled(severity(event.getLevel()))) {
                            delegate.doAppend(event);
                        }
                    });
                }
            };
            appender.setName("helidon-otel-logging-" + UUID.randomUUID());
            appender.setContext(logging);
        }

        @Override
        public void start() {
            lock.lock();
            try {
                if (state != State.NEW) {
                    throw new IllegalStateException("Logging bridge startup requires the new state");
                }
                state = State.STARTING;
                delegate.start();
                logging.addListener(this);
                attach();
                state = State.ACTIVE;
            } catch (RuntimeException | Error failure) {
                if (state == State.STARTING) {
                    state = State.STOPPING;
                }
                throw failure;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean active() {
            lock.lock();
            try {
                return state == State.ACTIVE;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void stop() {
            lock.lock();
            try {
                if (state != State.STOPPED && state != State.CLOSED) {
                    state = State.STOPPING;
                    logging.removeListener(this);
                    root.detachAppender(appender);
                    state = State.STOPPED;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (state == State.CLOSED) {
                    return;
                }
                stop();
                appender.stop();
                delegate.stop();
                state = State.CLOSED;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean isResetResistant() {
            return true;
        }

        @Override
        public void onStart(LoggerContext context) {
            attach();
        }

        @Override
        public void onReset(LoggerContext context) {
            attach();
        }

        @Override
        public void onStop(LoggerContext context) {
            lock.lock();
            try {
                if (state == State.STARTING || state == State.ACTIVE) {
                    state = State.STOPPING;
                    root.detachAppender(appender);
                    // Logback clears its listener list after this callback. Do not mutate that iteration.
                    state = State.STOPPED;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onLevelChange(Logger logger, Level level) {
        }

        private static Severity severity(Level level) {
            return switch (level.toInt()) {
            case Level.ERROR_INT -> Severity.ERROR;
            case Level.WARN_INT -> Severity.WARN;
            case Level.INFO_INT -> Severity.INFO;
            case Level.DEBUG_INT -> Severity.DEBUG;
            case Level.TRACE_INT, Level.ALL_INT -> Severity.TRACE;
            default -> Severity.UNDEFINED_SEVERITY_NUMBER;
            };
        }

        private void attach() {
            lock.lock();
            try {
                if (state == State.STARTING || state == State.ACTIVE) {
                    if (!appender.isStarted()) {
                        appender.start();
                    }
                    if (!root.isAttached(appender)) {
                        root.addAppender(appender);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private enum State {
            NEW,
            STARTING,
            ACTIVE,
            STOPPING,
            STOPPED,
            CLOSED
        }
    }
}

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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.logging.common.spi.LoggingProvider;
import io.helidon.logging.log4j.Log4jProvider;
import io.helidon.telemetry.logging.LogBridge;
import io.helidon.telemetry.logging.LogBridgeContext;
import io.helidon.telemetry.logging.spi.LogBridgeProvider;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

/**
 * Creates a deployment-owned Log4j 2 appender using the selected SDK.
 */
@Api.Internal
public final class Log4jLogBridgeProvider implements LogBridgeProvider {
    /**
     * Service-loader constructor.
     */
    public Log4jLogBridgeProvider() {
    }

    @Override
    public boolean supports(LoggingProvider provider) {
        return Objects.requireNonNull(provider, "provider") instanceof Log4jProvider
                && LogManager.getContext(false) instanceof LoggerContext;
    }

    @Override
    public LogBridge create(LogBridgeContext context) {
        Objects.requireNonNull(context, "context");
        if (!(LogManager.getContext(false) instanceof LoggerContext logging)) {
            throw new IllegalStateException("Log4j logging adapter requires an active Log4j Core logger context");
        }
        return new Registration(logging, context);
    }

    private static final class Registration implements LogBridge, PropertyChangeListener {
        private final ReentrantLock lock = new ReentrantLock();
        private final LoggerContext logging;
        private final OpenTelemetryAppender delegate;
        private final AbstractAppender appender;
        private Configuration installed;
        private final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();
        private State state = State.NEW;

        private Registration(LoggerContext logging, LogBridgeContext context) {
            this.logging = logging;
            String name = "helidon-otel-logging-" + UUID.randomUUID();
            delegate = OpenTelemetryAppender.builder().setName(name + "-otel").setOpenTelemetry(context.telemetry()).build();
            appender = new AbstractAppender(name, null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
                    var producer = LogBridgeContextDataInjector.context(event);
                    context.publish(loggerName, producer, () -> {
                        var logger = loggers.computeIfAbsent(loggerName.isEmpty() ? "ROOT" : loggerName,
                                                            context.telemetry().getLogsBridge()::get);
                        if (logger.isEnabled(severity(event.getLevel()), producer)) {
                            delegate.append(event);
                        }
                    });
                }
            };
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
                logging.addPropertyChangeListener(this);
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
                    logging.removePropertyChangeListener(this);
                    if (installed != null) {
                        detach(installed);
                        logging.updateLoggers();
                    }
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
                if (installed != null) {
                    installed.getAppenders().remove(appender.getName(), appender);
                }
                appender.stop();
                delegate.stop();
                state = State.CLOSED;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (LoggerContext.PROPERTY_CONFIG.equals(event.getPropertyName())) {
                attach();
            }
        }

        private void detach(Configuration configuration) {
            if (configuration.getRootLogger().getAppenders().get(appender.getName()) == appender) {
                configuration.getRootLogger().removeAppender(appender.getName());
            }
        }

        private static Severity severity(Level level) {
            return switch (level.getStandardLevel()) {
            case FATAL -> Severity.FATAL;
            case ERROR -> Severity.ERROR;
            case WARN -> Severity.WARN;
            case INFO -> Severity.INFO;
            case DEBUG -> Severity.DEBUG;
            case TRACE, ALL -> Severity.TRACE;
            case OFF -> Severity.UNDEFINED_SEVERITY_NUMBER;
            };
        }

        private void attach() {
            lock.lock();
            try {
                if (state != State.STARTING && state != State.ACTIVE) {
                    return;
                }
                var configuration = logging.getConfiguration();
                // updateLoggers itself notifies configuration listeners; do not reinstall our own hook.
                if (installed == configuration && appender.isStarted()
                        && configuration.getRootLogger().getAppenders().get(appender.getName()) == appender) {
                    return;
                }
                if (installed != null && installed != configuration) {
                    detach(installed);
                    installed.getAppenders().remove(appender.getName(), appender);
                }
                installed = configuration;
                if (!appender.isStarted()) {
                    appender.start();
                }
                configuration.addAppender(appender);
                configuration.getRootLogger().addAppender(appender, null, null);
                logging.updateLoggers();
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

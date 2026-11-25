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

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.common.Api;
import io.helidon.logging.common.spi.LoggingProvider;
import io.helidon.logging.jul.JulProvider;
import io.helidon.telemetry.logging.LogBridge;
import io.helidon.telemetry.logging.LogBridgeContext;
import io.helidon.telemetry.logging.spi.LogBridgeProvider;

/**
 * Installs an owned JUL handler without changing application logging configuration.
 */
@Api.Internal
public final class JulLogBridgeProvider implements LogBridgeProvider {
    /**
     * Service-loader constructor.
     */
    public JulLogBridgeProvider() {
    }

    @Override
    public boolean supports(LoggingProvider provider) {
        return Objects.requireNonNull(provider, "provider") instanceof JulProvider;
    }

    @Override
    public LogBridge create(LogBridgeContext context) {
        return new Registration(Objects.requireNonNull(context, "context"));
    }

    private static final class Registration implements LogBridge {
        private final ReentrantLock lock = new ReentrantLock();
        private final Logger root = Logger.getLogger("");
        private final TelemetryLogHandler handler;
        private final Runnable reconfigure = this::attach;
        private State state = State.NEW;

        private Registration(LogBridgeContext context) {
            handler = new TelemetryLogHandler(context);
        }

        @Override
        public void start() {
            lock.lock();
            try {
                if (state != State.NEW) {
                    throw new IllegalStateException("Logging bridge startup requires the new state");
                }
                state = State.STARTING;
                LogManager.getLogManager().addConfigurationListener(reconfigure);
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
                    LogManager.getLogManager().removeConfigurationListener(reconfigure);
                    root.removeHandler(handler);
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
                handler.close();
                state = State.CLOSED;
            } finally {
                lock.unlock();
            }
        }

        private void attach() {
            lock.lock();
            try {
                if ((state == State.STARTING || state == State.ACTIVE)
                        && !Arrays.asList(root.getHandlers()).contains(handler)) {
                    root.addHandler(handler);
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

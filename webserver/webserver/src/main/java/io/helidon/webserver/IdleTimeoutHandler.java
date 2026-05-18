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

package io.helidon.webserver;

import java.time.Duration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

class IdleTimeoutHandler extends TimerTask {
    private final Timer timer;
    private final Supplier<List<ConnectionHandler>> handlerSupplier;
    private final Duration timeout;
    private final Duration period;
    private final String taskName;
    private final Lock runLock = new ReentrantLock();
    private volatile boolean cancelled;

    IdleTimeoutHandler(Timer timer,
                       ListenerConfig listenerConfig,
                       Supplier<List<ConnectionHandler>> handlerSupplier) {
        this.timer = timer;
        this.handlerSupplier = handlerSupplier;
        this.timeout = listenerConfig.idleConnectionTimeout();
        this.period = listenerConfig.idleConnectionPeriod();

        String listenerName = listenerConfig.name();
        this.taskName = "idle-timeout-handler-" + listenerName;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        runLock.lock();
        try {
            if (cancelled) {
                return;
            }
            Thread.currentThread().setName(taskName);
            List<ConnectionHandler> connectionHandlers = handlerSupplier.get();
            for (ConnectionHandler connectionHandler : connectionHandlers) {
                try {
                    connectionHandler.closeIfIdle(timeout);
                } catch (Throwable t) {
                    System.getLogger(IdleTimeoutHandler.class.getName() + "." + taskName)
                            .log(System.Logger.Level.TRACE, "Failed to close an idle connection", t);
                }
            }
        } finally {
            Thread.currentThread().setName(name);
            runLock.unlock();
        }
    }

    void start() {
        timer.schedule(this, period.toMillis(), period.toMillis());
    }

    void cancelAndAwait() {
        cancelled = true;
        cancel();
        runLock.lock();
        try {
            // Wait for an in-flight idle timeout pass to finish.
        } finally {
            runLock.unlock();
        }
    }
}

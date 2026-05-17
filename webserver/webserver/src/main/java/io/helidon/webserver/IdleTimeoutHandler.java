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
import java.util.function.Supplier;

class IdleTimeoutHandler extends TimerTask {
    private final Timer timer;
    private final Supplier<List<ConnectionHandler>> handlerSupplier;
    private final Duration timeout;
    private final Duration period;
    private final String taskName;

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
        Thread.currentThread().setName(name);
    }

    void start() {
        timer.schedule(this, period.toMillis(), period.toMillis());
    }
}

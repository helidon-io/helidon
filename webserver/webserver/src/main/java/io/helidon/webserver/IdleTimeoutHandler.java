/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.webserver.spi.ServerConnection;

class IdleTimeoutHandler extends TimerTask {
    private final Timer timer;
    private final Supplier<List<ServerConnection>> connectionSupplier;
    private final Duration timeout;
    private final Duration period;
    private final String taskName;

    IdleTimeoutHandler(Timer timer,
                       ListenerConfig listenerConfig,
                       Supplier<List<ServerConnection>> connectionSupplier) {
        this.timer = timer;
        this.connectionSupplier = connectionSupplier;
        this.timeout = listenerConfig.idleConnectionTimeout();
        this.period = listenerConfig.idleConnectionPeriod();

        String listenerName = listenerConfig.name();
        this.taskName = "idle-timeout-handler-" + listenerName;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        Thread.currentThread().setName(taskName);
        List<ServerConnection> serverConnections = connectionSupplier.get();
        for (ServerConnection serverConnection : serverConnections) {
            if (serverConnection.idleTime().compareTo(timeout) > 0) {
                // this should be a graceful shutdown, in case a request is received in parallel, we want to handle
                // it, and yes, then it would be closed (and it must not accept another request)
                try {
                    serverConnection.close(false);
                } catch (Throwable t) {
                    System.getLogger(IdleTimeoutHandler.class.getName() + "." + taskName)
                            .log(System.Logger.Level.TRACE, "Failed to close an idle connection", t);
                }
            }
        }
        Thread.currentThread().setName(name);
    }

    void start() {
        timer.schedule(this, period.toMillis(), period.toMillis());
    }
}

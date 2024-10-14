/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.spi;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.NoopSemaphore;

/**
 * Server connection abstraction, used by any provider to handle a socket connection.
 */
public interface ServerConnection {
    /**
     * Start handling the connection. Data is provided through
     * {@link ServerConnectionSelector#connection(io.helidon.webserver.ConnectionContext)}.
     *
     * @param requestSemaphore semaphore that is responsible for maximal concurrent request limit, the connection implementation
     *                         is responsible for acquiring a permit from the semaphore for the duration of a request, and
     *                         releasing it when the request ends; please be very careful, as this may lead to complete stop
     *                         of the server if incorrectly implemented
     * @throws InterruptedException to interrupt any waiting state and terminate this connection
     * @deprecated implement {@link #handle(io.helidon.common.concurrency.limits.Limit)} instead
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    default void handle(Semaphore requestSemaphore) throws InterruptedException {
        throw new IllegalStateException("This method must be implemented, unless handle(Limit) is implemented");
    }

    /**
     * Start handling the connection. Data is provided through
     * {@link ServerConnectionSelector#connection(io.helidon.webserver.ConnectionContext)}.
     *
     * @param limit that is responsible for maximal concurrent request limit, the connection implementation
     *              is responsible invoking each request within the limit's
     *              {@link io.helidon.common.concurrency.limits.Limit#invoke(java.util.concurrent.Callable)}
     * @throws InterruptedException to interrupt any waiting state and terminate this connection
     */
    @SuppressWarnings("removal") // usage will be removed with the deprecated types
    default void handle(Limit limit) throws InterruptedException {
        if (limit instanceof io.helidon.common.concurrency.limits.SemaphoreLimit sl) {
            handle(sl.semaphore());
        } else {
            handle(NoopSemaphore.INSTANCE);
        }
    }

    /**
     * How long is this connection idle. This is a duration from the last request to now.
     *
     * @return idle duration
     */
    Duration idleTime();

    /**
     * Close a connection. This may be called during shutdown of the server, or when idle timeout is reached.
     *
     * @param interrupt whether to interrupt in progress requests (always interrupts idle requests waiting for
     *                  initial request data)
     */
    void close(boolean interrupt);
}

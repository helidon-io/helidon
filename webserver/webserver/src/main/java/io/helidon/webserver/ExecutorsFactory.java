/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.helidon.common.task.HelidonTaskExecutor;

/**
 * Creates virtual-thread executors used by the webserver.
 */
final class ExecutorsFactory {
    private static final System.Logger LOGGER = System.getLogger(ExecutorsFactory.class.getName());
    private static final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER =
            (thread, throwable) -> LOGGER.log(System.Logger.Level.ERROR,
                                              "Uncaught exception in WebServer executor thread " + thread.getName(),
                                              throwable);

    private ExecutorsFactory() {
    }

    /**
     * Used by {@link LoomServer} to allocate its executor service.
     *
     * @return {@link Executors#newThreadPerTaskExecutor(java.util.concurrent.ThreadFactory)}
     */
    static ExecutorService newLoomServerVirtualThreadPerTaskExecutor() {
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory());
    }

    /**
     * Used by {@link SocketTransportBinding} to allocate its reader executor.
     *
     * @return {@link ThreadPerTaskExecutor#create(java.util.concurrent.ThreadFactory)}
     */
    static HelidonTaskExecutor newServerListenerReaderExecutor() {
        return ThreadPerTaskExecutor.create(Thread.ofVirtual().factory());
    }

    /**
     * Used by {@link ServerListener} to allocate its shared executor.
     *
     * @return {@link Executors#newThreadPerTaskExecutor(java.util.concurrent.ThreadFactory)}.
     */
    static ExecutorService newServerListenerSharedExecutor() {
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory());
    }

    private static ThreadFactory virtualThreadFactory() {
        return Thread.ofVirtual()
                .uncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER)
                .factory();
    }
}

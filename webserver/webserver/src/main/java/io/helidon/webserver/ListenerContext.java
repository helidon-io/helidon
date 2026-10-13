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

import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.helidon.common.context.Context;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;

/**
 * Listener context.
 * Provides elements that are configured on listener (socket) level.
 */
public interface ListenerContext {
    /**
     * Log an unexpected internal server failure using the server's error log limits.
     * The message must identify the logging site and must not contain request data or exception messages.
     * The full failure is logged for allowed occurrences; it is not retained by the limiter.
     * The default implementation logs directly for independently implemented listener contexts.
     *
     * @param logger logger for the diagnostic
     * @param level log level
     * @param message fixed diagnostic message
     * @param failure failure to log
     */
    default void logError(System.Logger logger, System.Logger.Level level, String message, Throwable failure) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(failure, "failure");
        logger.log(level, message, failure);
    }

    /**
     * Log an unexpected internal server error without an associated throwable.
     * The message must be a fixed diagnostic identifying the logging site.
     *
     * @param logger logger for the diagnostic
     * @param level log level
     * @param message fixed diagnostic message
     */
    default void logError(System.Logger logger, System.Logger.Level level, String message) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
        logger.log(level, message);
    }

    /**
     * Server context configured as the top level parents of all request context.
     *
     * @return server context, always available
     */
    Context context();

    /**
     * Media context to read and write typed entities.
     *
     * @return media context
     */
    MediaContext mediaContext();

    /**
     * Content encoding support, to handle entity encoding (such as gzip, deflate).
     *
     * @return content encoding support
     */
    ContentEncodingContext contentEncodingContext();

    /**
     * Direct handlers for non-routing exception handling.
     *
     * @return direct handlers
     */
    DirectHandlers directHandlers();

    /**
     * Configuration of this listener.
     *
     * @return listener configuration
     */
    ListenerConfig config();

    /**
     * Virtual thread per task executor service that can be used to execute tasks.
     * Tasks submitted on this executor are considered to be tasks that must be completed before graceful shutdown.
     *
     * @return executor service
     */
    ExecutorService executor();
}

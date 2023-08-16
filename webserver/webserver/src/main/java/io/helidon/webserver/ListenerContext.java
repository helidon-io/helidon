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

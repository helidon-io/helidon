/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.context.Context;

/**
 * A service that is registered with {@link io.helidon.webserver.ServerConfiguration}
 * and that provides either application wide configuration through
 * {@link #updateApplicationContext(io.helidon.common.context.Context)},
 * or request specific configuration through
 * {@link #handle(io.helidon.webserver.ServerRequest, io.helidon.webserver.ServerResponse)}.
 *
 * The system service cannot invoke {@link io.helidon.webserver.ServerRequest#next()}, not
 * any of the {@link io.helidon.webserver.ServerResponse#send()} methods.
 * Invocation of such methods will throw an {@link java.lang.UnsupportedOperationException}.
 */
public interface SystemService {
    /**
     * Provides possibility for a system service to update the application
     * wide {@link io.helidon.common.context.Context}.
     * The application context is a parent of all request contexts.
     *
     * @param context application wide context to update
     */
    default void updateApplicationContext(Context context) {
    }

    /**
     * Handle a per-request configuration.
     *
     * @param request to read headers, register readers etc.
     * @param response to write headers, register writers etc.
     */
    default void handle(ServerRequest request, ServerResponse response) {
    }
}

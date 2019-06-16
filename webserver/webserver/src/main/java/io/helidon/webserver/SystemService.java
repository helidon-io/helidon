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
 * {@link #processRequest(ServerRequest, ServerResponse)}.
 *
 * The system service can invoke neither {@link io.helidon.webserver.ServerRequest#next()} nor
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
     * Handle a per-request configuration. This method is called before routing methods are invoked.
     * <p>
     * Attempt to call {@link io.helidon.webserver.ServerRequest#next()} or any of the
     * {@link io.helidon.webserver.ServerResponse#send()} methods will result in an
     * {@link java.lang.UnsupportedOperationException}.
     * <p>
     * Behavior when the entity content is read by a system service is undefined.
     *
     * @param request to read headers, register readers etc.
     * @param response to write headers, register writers etc.
     */
    default void processRequest(ServerRequest request, ServerResponse response) {
    }

    /**
     * This method allows modification of the response. This method is called after the routing method
     * invoked any of the {@code send} methods, but before the entity is actually written.
     * The {@link ServerResponse#headers()} can be still modified.
     * <p>
     * Attempt to call {@link io.helidon.webserver.ServerRequest#next()} or any of the
     * {@link io.helidon.webserver.ServerResponse#send()} methods will result in an
     * {@link java.lang.UnsupportedOperationException}.
     *
     * @param request to read headers
     * @param response to write headers, register writers etc.
     */
    default void processResponse(ServerRequest request, ServerResponse response) {
    }
}

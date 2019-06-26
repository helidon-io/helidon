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
package io.helidon.webserver.accesslog;

import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * An entry generating data for access log.
 * Implementation MUST be thread safe.
 */
@FunctionalInterface
public interface AccessLogEntry extends Handler {
    /**
     * If an entry is not available, use this string as the result.
     */
    String NOT_AVAILABLE = "-";

    /**
     * This method allows for each log entry to register anything on the request and/or response.
     * As the log entry is shared by all threads, make sure the handling is implemented as thread safe.
     *
     * @param req an HTTP server request.
     * @param res an HTTP server response.
     */
    @Override
    default void accept(ServerRequest req, ServerResponse res) {
    }

    /**
     * This method is called once the response is fully processed.
     * The {@link AccessLogContext#serverResponse()} will return a completed response.
     *
     * @param context context with access to information useful for access log entries
     * @return string representation of a log entry (such as a formatted date time, response time etc.)
     */
    String apply(AccessLogContext context);
}

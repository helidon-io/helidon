/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.accesslog;

import java.time.ZonedDateTime;

import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;

/**
 * Context of the access log processing.
 */
public interface AccessLogContext {
    /**
     * Time in {@link System#nanoTime()} when the request processing started.
     *
     * @return time in nanoseconds
     */
    long requestNanoTime();

    /**
     * Time in {@link System#nanoTime()} when the response processing finished (last byte written).
     *
     * @return time in nanoseconds
     */
    long responseNanoTime();

    /**
     * Time when the request started processing.
     *
     * @return time of the request
     */
    ZonedDateTime requestDateTime();

    /**
     * Time when the response finished processing.
     *
     * @return time of the response
     */
    ZonedDateTime responseDateTime();

    /**
     * The server request.
     *
     * @return server request
     */
    RoutingRequest serverRequest();

    /**
     * The server response, after data was sent.
     *
     * @return server response
     */
    RoutingResponse serverResponse();
}

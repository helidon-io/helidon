/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.blocking;

import java.util.function.Consumer;

import io.helidon.common.http.Http;
import io.helidon.webserver.ResponseHeaders;

/**
 * Abstraction of HTTP response that uses fully blocking methods.
 */
public interface BlockingResponse {
    /**
     * Send a message and close the response.
     *
     * Type must be supported by one of the registered writers using
     * {@link io.helidon.webserver.WebServer.Builder#addWriter(io.helidon.media.common.MessageBodyWriter)}
     * or
     * {@link io.helidon.webserver.WebServer.Builder#addMediaSupport(io.helidon.media.common.MediaSupport)}.
     *
     * @param content response content to send
     * @param <T>     type of the content
     *
     * @throws IllegalArgumentException if there is no registered writer for a given type
     * @throws IllegalStateException if any {@code send(...)} method was already called
     */
    <T> void send(T content);

    /**
     * Sends an empty response. Does nothing if response was already send.
     */
    void send();

    /**
     * Sets new HTTP status code. Can be done before headers are completed - see {@link ResponseHeaders} documentation.
     *
     * @param status new status code, such as {@link Http.Status#ACCEPTED_202}
     * @throws io.helidon.common.http.AlreadyCompletedException if headers were completed (sent to the client)
     * @return this instance
     */
    BlockingResponse status(Http.ResponseStatus status);

    /**
     * Returns actual response status code.
     * <p>
     * Default value for handlers is {@code 200} and for failure handlers {@code 500}. Value can be redefined using
     * {@link #status(io.helidon.common.http.Http.ResponseStatus)} method before headers are send.
     *
     * @return an HTTP status code
     */
    Http.ResponseStatus status();

    /**
     * Returns response headers. Headers can be modified before they are sent to the client.
     *
     * @return response headers
     */
    ResponseHeaders headers();

    /**
     * Support for fluent API way of updating response headers.
     *
     * @param updater code that uses response headers to update them
     * @return this instance
     */
    default BlockingResponse updateHeaders(Consumer<ResponseHeaders> updater) {
        updater.accept(headers());
        return this;
    }
}

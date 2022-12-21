/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import java.io.OutputStream;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.NotFoundException;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.uri.UriQuery;

/**
 * Http server response.
 */
public interface ServerResponse {
    /**
     * Status of the response.
     *
     * @param status HTTP status
     * @return this instance
     */
    ServerResponse status(Http.Status status);

    /**
     * Configured HTTP status, if not configured, returns {@link Http.Status#OK_200}.
     *
     * @return status
     */
    Http.Status status();

    /**
     * Set a header. If the values are constant, please use
     * {@link io.helidon.common.http.Http.Header#create(io.helidon.common.http.Http.HeaderName, String...)} and store the header
     * in a constant field and call {@link #header(Http.HeaderValue)}.
     *
     * @param name   header name
     * @param values value(s) of the header
     * @return this instance
     */
    default ServerResponse header(HeaderName name, String... values) {
        return header(Http.Header.create(name, values));
    }

    /**
     * Not optimized method for setting a header.
     * Use for unknown headers, where {@link HeaderName} cannot be cached.
     * Use {@link #header(Http.HeaderValue)} or {@link #header(HeaderName, String...)}
     * otherwise.
     *
     * @param name   name of the header
     * @param values values of the header
     * @return this instance
     */
    default ServerResponse header(String name, String... values) {
        return header(Http.Header.create(Http.Header.create(name), values));
    }

    /**
     * Set header with a value.
     *
     * @param header header value
     * @return this instance
     * @see HeaderName
     */
    ServerResponse header(Http.HeaderValue header);

    /**
     * Send a response with no entity.
     */
    void send();

    /**
     * Send a byte array response.
     *
     * @param bytes bytes to send
     */
    void send(byte[] bytes);

    /**
     * Send an entity, a {@link io.helidon.nima.http.media.MediaContext} will be used to serialize the entity.
     *
     * @param entity entity object
     */
    void send(Object entity);

    /**
     * Send an entity if present, throw {@link io.helidon.common.http.NotFoundException} if empty.
     *
     * @param entity entity as an optional
     */
    default void send(Optional<?> entity) {
        send(entity.orElseThrow(() -> new NotFoundException("")));
    }

    /**
     * Whether this response has been sent.
     *
     * @return whether sent
     */
    boolean isSent();

    /**
     * Alternative way to send an entity, using an output stream. This should be used for entities that are big
     * and that should not be materialized into memory.
     *
     * @return output stream
     */
    OutputStream outputStream();

    /**
     * Number of bytes written to response. Only available once the response is sent.
     *
     * @return bytes written (combination of all bytes of status, headers and entity)
     */
    long bytesWritten();

    /**
     * Completed when last byte is buffered for socket write.
     *
     * @param listener listener to add to list of listeners that will be triggered once response is sent
     * @return this instance
     */
    ServerResponse whenSent(Runnable listener);

    /**
     * Re-route using a different path.
     *
     * @param newPath new path to use
     * @return this instance
     */
    ServerResponse reroute(String newPath);

    /**
     * Re-route using a different path and query.
     *
     * @param path  new path
     * @param query new query
     * @return this instance
     * @throws IllegalStateException in case the entity was already configured
     * @see io.helidon.common.uri.UriQuery
     * @see io.helidon.common.uri.UriQueryWriteable
     */
    ServerResponse reroute(String path, UriQuery query);

    /**
     * Continue processing with the next route (and if none found, return a {@link Http.Status#NOT_FOUND_404}).
     * If any entity method was called, this method will throw an exception.
     *
     * @return this instance
     * @throws IllegalStateException in case the entity was already configured
     */
    ServerResponse next();

    /**
     * Response headers (mutable).
     *
     * @return headers
     */
    ServerResponseHeaders headers();

    /**
     * Description of the result of output stream processing.
     * In case an output stream was used, calling this method will immediately close the stream and return this
     * message as the reason for closing the response.
     * In HTTP/1 this would be in a trailer header
     *
     * @param result result description
     */
    void streamResult(String result);

    /**
     * Configure a content length header for this response.
     *
     * @param length content length
     */
    default void contentLength(long length) {
        header(Http.Header.create(Http.Header.CONTENT_LENGTH, true, false, String.valueOf(length)));
    }
}

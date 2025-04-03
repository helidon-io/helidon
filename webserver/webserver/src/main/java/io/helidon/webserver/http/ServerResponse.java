/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.helidon.common.GenericType;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.NotFoundException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.spi.Sink;

/**
 * Http server response.
 */
@Service.Describe(Service.PerRequest.class)
public interface ServerResponse {
    /**
     * Status of the response.
     *
     * @param status HTTP status
     * @return this instance
     */
    ServerResponse status(Status status);

    /**
     * Status of the response.
     *
     * @param status HTTP status as integer
     * @return this instance
     */
    default ServerResponse status(int status) {
        return status(Status.create(status));
    }

    /**
     * Configured HTTP status, if not configured, returns {@link io.helidon.http.Status#OK_200}.
     *
     * @return status
     */
    Status status();

    /**
     * Set a header. If the values are constant, please use
     * {@link io.helidon.http.HeaderValues#create(io.helidon.http.HeaderName, String...)} and store the header
     * in a constant field and call {@link #header(io.helidon.http.Header)}.
     *
     * @param name   header name
     * @param values value(s) of the header
     * @return this instance
     */
    default ServerResponse header(HeaderName name, String... values) {
        return header(HeaderValues.create(name, values));
    }

    /**
     * Not optimized method for setting a header.
     * Use for unknown headers, where {@link HeaderName} cannot be cached.
     * Use {@link #header(io.helidon.http.Header)} or {@link #header(HeaderName, String...)}
     * otherwise.
     *
     * @param name   name of the header
     * @param values values of the header
     * @return this instance
     */
    default ServerResponse header(String name, String... values) {
        return header(HeaderValues.create(name, values));
    }

    /**
     * Set header with a value.
     * Headers cannot be set after {@link #outputStream()} method is called, or after the response was sent.
     *
     * @param header header value
     * @return this instance
     * @throws java.lang.IllegalStateException in case a header is set after output stream was requested,
     *              or the response was sent
     * @see HeaderName
     */
    ServerResponse header(Header header);

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
     * Send a byte array response.
     *
     * @param bytes bytes to send
     * @param position starting position
     * @param length number of bytes send
     */
    default void send(byte[] bytes, int position, int length) {
        send(Arrays.copyOfRange(bytes, position, length));
    }

    /**
     * Send an entity, a {@link io.helidon.http.media.MediaContext} will be used to serialize the entity.
     *
     * @param entity entity object
     */
    void send(Object entity);

    /**
     * Send an entity if present, throw {@link io.helidon.http.NotFoundException} if empty.
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
     * Continue processing with the next route (and if none found, return a {@link io.helidon.http.Status#NOT_FOUND_404}).
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
     * Response trailers (mutable).
     * @return trailers
     * @throws java.lang.IllegalStateException if client didn't ask for trailers with {@code TE: trailers} header in request
     * or response doesn't contain trailer declaration headers {@code Trailer: <trailer-name>}
     */
    ServerResponseTrailers trailers();

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
        header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, true, false, String.valueOf(length)));
    }

    /**
     * Returns a sink from this response based on the sink type, if available.
     *
     * @param sinkType type of sink
     * @return sink or {@code null} if not available
     * @param <T> type of sink returned
     */
    default <T extends Sink<?>> T sink(GenericType<T> sinkType) {
        throw new UnsupportedOperationException("No sink available for type " + sinkType);
    }

    /**
     * Configure a custom output stream to wrap the output stream of the response.
     *
     * @param filterFunction the function to replace output stream of this response with a user provided one
     */
    void streamFilter(UnaryOperator<OutputStream> filterFunction);
}

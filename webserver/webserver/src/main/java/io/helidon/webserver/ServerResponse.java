/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyFilter;
import io.helidon.media.common.MessageBodyFilters;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.common.MessageBodyWriters;

/**
 * Represents HTTP Response.
 *
 * <h2>Lifecycle</h2>
 * HTTP response is send to the client in two or more steps (chunks). First contains {@link #status() HTTP status code}
 * and {@link ResponseHeaders headers}. First part can be send explicitly by calling {@link ResponseHeaders#send()}
 * method or implicitly by sending a first part of the the response content. As soon as first part is send it become immutable -
 * {@link #status(int)} method and all muting operations of {@link ResponseHeaders} will throw {@link IllegalStateException}.
 * <p>
 * Response content (body/payload) can be constructed using {@link #send(Object) send(...)} methods.
 */
public interface ServerResponse extends MessageBodyFilters, MessageBodyWriters {

    /**
     * Declares common groups of {@code Cache-Control} settings.
     * <p>
     *     Inspired by the {@code CacheControl} class in JAX-RS.
     * </p>
     */
    enum CachingStrategy {
        /**
         * Normal cache control: permit caching but prohibit transforming the content.
         */
        NORMAL("no-transform"),

        /**
         * Discourage caching.
         */
        NO_CACHING("no-cache", "no-store", "must-revalidate", "no-transform");

        private final String[] cacheControlHeaderValues;

        CachingStrategy(String... cacheControlHeaderValues) {
            this.cacheControlHeaderValues = cacheControlHeaderValues;
        }

        private ServerResponse apply(ServerResponse serverResponse) {
            serverResponse.addHeader(Http.Header.CACHE_CONTROL, cacheControlHeaderValues);
            return serverResponse;
        }
    }
    /**
     * Returns actual {@link WebServer} instance.
     *
     * @return an actual {@code WebServer} instance
     */
    WebServer webServer();

    /**
     * Returns actual response status code.
     * <p>
     * Default value for handlers is {@code 200} and for failure handlers {@code 500}. Value can be redefined using
     * {@link #status(int)} method before headers are send.
     *
     * @return an HTTP status code
     */
    Http.Status status();

    /**
     * Sets new HTTP status code. Can be done before headers are completed - see {@link ResponseHeaders} documentation.
     *
     * @param statusCode new status code
     * @throws AlreadyCompletedException if headers were completed (sent to the client)
     * @return this instance of {@link ServerResponse}
     */
    default ServerResponse status(int statusCode) throws AlreadyCompletedException {
        return status(Http.Status.create(statusCode));
    }

    /**
     * Sets new HTTP status. Can be done before headers are completed - see {@link ResponseHeaders} documentation.
     *
     * @param status new status
     * @return this instance of {@link ServerResponse}
     * @throws AlreadyCompletedException if headers were completed (sent to the client)
     * @throws NullPointerException      if status parameter is {@code null}
     */
    ServerResponse status(Http.Status status) throws AlreadyCompletedException, NullPointerException;

    /**
     * Returns response headers. It can be modified before headers are sent to the client.
     *
     * @return a response headers
     */
    ResponseHeaders headers();

    /**
     * Adds header values for a specified name.
     *
     * @param name   header name
     * @param values header values
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if the specified name is null.
     * @see #headers()
     * @see Http.Header header names constants
     * @deprecated use {@link #addHeader(io.helidon.common.http.Http.HeaderName, String...)}
     */
    @Deprecated
    default ServerResponse addHeader(String name, String... values) {
        return addHeader(Http.Header.create(name), values);
    }

    /**
     * Adds header values for a specified name.
     *
     * @param name   header name
     * @param values header values
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if the specified name is null.
     * @see #headers()
     * @see Http.Header header names constants
     * @deprecated use {@link #addHeader(io.helidon.common.http.Http.HeaderName, String...)}
     */
    @Deprecated
    default ServerResponse addHeader(String name, List<String> values) {
        headers().add(Http.HeaderValue.create(Http.Header.create(name), values));
        return this;
    }

    /**
     * Adds header values for a specified name.
     *
     * @param name   header name
     * @param values header values
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if the specified name is null.
     * @see #headers()
     * @see Http.Header header names constants
     */
    default ServerResponse addHeader(Http.HeaderName name, String... values) {
        headers().add(Http.HeaderValue.create(name, values));
        return this;
    }

    /**
     * Adds header values for a specified name.
     *
     * @param value header value
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if the specified name is null.
     * @see #headers()
     * @see Http.Header header names constants
     */
    default ServerResponse addHeader(Http.HeaderValue value) {
        headers().add(value);
        return this;
    }

    /**
     * Sets the {@code Cache-Control} header values according to the specified strategy.
     *
     * @param cachingStrategy {@link io.helidon.webserver.ServerResponse.CachingStrategy} to apply to this response
     * @return updated response
     */
    default ServerResponse cachingStrategy(CachingStrategy cachingStrategy) {
        cachingStrategy.apply(this);
        return this;
    }

    /**
     * Get the writer context used to marshall data.
     *
     * @return MessageBodyWriterContext
     */
    MessageBodyWriterContext writerContext();

    /**
     * Send a {@link Throwable} and close the response.
     * Invokes error handlers if defined.
     *
     * @param content the {@link Throwable} to send
     * @return {@code null} when invoked
     * @throws IllegalStateException if any {@code send(...)} method was already called
     * @see #send(Object)
     */
    Void send(Throwable content);

    /**
     * Send a message and close the response.
     *
     * <h4>Marshalling</h4>
     * Data are marshaled using default or {@link #registerWriter(io.helidon.media.common.MessageBodyWriter) registered}
     * {@code writer} to the format
     * of {@link ByteBuffer} {@link Publisher Publisher}. The last registered compatible writer is used.
     * <p>
     * Default writers supports:
     * <ul>
     *     <li>{@link CharSequence}</li>
     *     <li>{@code byte[]}</li>
     *     <li>{@link java.nio.file.Path}</li>
     *     <li>{@link java.io.File}</li>
     * </ul>
     *
     * <h4>Blocking</h4>
     * The method blocks only during marshalling. It means until {@code registered writer} produce a {@code Publisher} and
     * subscribe HTTP IO implementation on it. If the thread is used for publishing is up to HTTP IO and generated Publisher
     * implementations. Use returned {@link io.helidon.common.reactive.Single} to monitor and react on finished sending process.
     *
     * @param content a response content to send
     * @param <T>     a type of the content
     * @return a completion stage of the response - completed when response is transferred
     * @throws IllegalArgumentException if there is no registered writer for a given type
     * @throws IllegalStateException if any {@code send(...)} method was already called
     */
    <T> Single<ServerResponse> send(T content);

    /**
     * Send a message with the given entity stream as content and close the response.
     * @param <T> entity type
     * @param content entity stream
     * @param clazz class representing the entity type
     * @return a completion stage of the response - completed when response is transferred
     */
    <T> Single<ServerResponse> send(Publisher<T> content, Class<T> clazz);

    /**
     * Send a message as is without any other marshalling, registered filters are applied.
     * The response is completed when publisher send
     * {@link Subscriber#onComplete()} method to its subscriber.
     * <p>
     * A single {@link Subscription Subscriber} subscribes to the provided {@link Publisher Publisher} during
     * the method execution.
     *
     * <h4>Blocking</h4>
     * If the thread is used for publishing is up to HTTP IO and generated Publisher
     * implementations. Use returned {@link io.helidon.common.reactive.Single} to monitor and react on finished sending process.
     *
     * @param content a response content publisher
     * @return a completion stage of the response - completed when response is transferred
     * @throws IllegalStateException if any {@code send(...)} method was already called
     */
    Single<ServerResponse> send(Publisher<DataChunk> content);


    /**
     * Send a message as is without any other marshalling. The response is completed when publisher send
     * {@link Subscriber#onComplete()} method to its subscriber.
     * <p>
     * A single {@link Subscription Subscriber} subscribes to the provided {@link Publisher Publisher} during
     * the method execution.
     *
     * <h4>Blocking</h4>
     * If the thread is used for publishing is up to HTTP IO and generated Publisher
     * implementations. Use returned {@link io.helidon.common.reactive.Single} to monitor and react on finished sending process.
     *
     * @param content a response content publisher
     * @param applyFilters if true all registered filters are applied
     * @return a completion stage of the response - completed when response is transferred
     * @throws IllegalStateException if any {@code send(...)} method was already called
     */
    Single<ServerResponse> send(Publisher<DataChunk> content, boolean applyFilters);

    /**
     * Send a message using the given marshalling function.
     *
     * @param function marshalling function
     * @return a completion stage of the response - completed when response is transferred
     */
    Single<ServerResponse> send(Function<MessageBodyWriterContext, Publisher<DataChunk>> function);

    /**
     * Sends an empty response. Do nothing if response was already send.
     *
     * @return a completion stage of the response - completed when response is transferred
     */
    Single<ServerResponse> send();

    @Override
    ServerResponse registerFilter(MessageBodyFilter filter);

    @Override
    ServerResponse registerWriter(MessageBodyWriter<?> writer);

    @Override
    ServerResponse registerWriter(MessageBodyStreamWriter<?> writer);

    /**
     * Completion stage is completed when response is completed.
     * <p>
     * It can be used to react on the response completion without invocation of a closing event.
     *
     * @return a completion stage of the response
     */
    Single<ServerResponse> whenSent();

    /**
     * A unique correlation ID that is associated with this response and its associated request.
     *
     * @return a unique correlation ID associated with this response and its request
     */
    long requestId();
}

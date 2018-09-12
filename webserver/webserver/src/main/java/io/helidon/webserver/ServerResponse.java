/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.http.AlreadyCompletedException;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Flow;

/**
 * Represents HTTP Response.
 *
 * <h3>Lifecycle</h3>
 * HTTP response is send to the client in two or more steps (chunks). First contains {@link #status() HTTP status code}
 * and {@link ResponseHeaders headers}. First part can be send explicitly by calling {@link ResponseHeaders#send()}
 * method or implicitly by sending a first part of the the response content. As soon as first part is send it become immutable -
 * {@link #status(int)} method and all muting operations of {@link ResponseHeaders} will throw {@link IllegalStateException}.
 * <p>
 * Response content (body/payload) can be constructed using {@link #send(Object) send(...)} methods.
 */
public interface ServerResponse {

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
    Http.ResponseStatus status();

    /**
     * Sets new HTTP status code. Can be done before headers are completed - see {@link ResponseHeaders} documentation.
     *
     * @param statusCode new status code
     * @throws AlreadyCompletedException if headers were completed (sent to the client)
     * @return this instance of {@link ServerResponse}
     */
    default ServerResponse status(int statusCode) throws AlreadyCompletedException {
        return status(Http.ResponseStatus.from(statusCode));
    }

    /**
     * Sets new HTTP status. Can be done before headers are completed - see {@link ResponseHeaders} documentation.
     *
     * @param status new status
     * @return this instance of {@link ServerResponse}
     * @throws AlreadyCompletedException if headers were completed (sent to the client)
     * @throws NullPointerException      if status parameter is {@code null}
     */
    ServerResponse status(Http.ResponseStatus status) throws AlreadyCompletedException, NullPointerException;

    /**
     * Returns response headers. It can be modified before headers are sent to the client.
     *
     * @return a response headers
     */
    ResponseHeaders headers();

    /**
     * Send a message and close the response.
     *
     * <h3>Marshalling</h3>
     * Data are marshaled using default or {@link #registerWriter(Class, Function) registered} {@code writer} to the format
     * of {@link ByteBuffer} {@link Flow.Publisher Publisher}. The last registered compatible writer is used.
     * <p>
     * Default writers supports:
     * <ul>
     *     <li>{@link CharSequence}</li>
     *     <li>{@code byte[]}</li>
     *     <li>{@link java.nio.file.Path}</li>
     *     <li>{@link java.io.File}</li>
     * </ul>
     *
     * <h3>Blocking</h3>
     * The method blocks only during marshalling. It means until {@code registered writer} produce a {@code Publisher} and
     * subscribe HTTP IO implementation on it. If the thread is used for publishing is up to HTTP IO and generated Publisher
     * implementations. Use returned {@link CompletionStage} to monitor and react on finished sending tryProcess.
     *
     * @param content a response content to send
     * @param <T>     a type of the content
     * @return a completion stage of the response - completed when response is transferred
     * @throws IllegalArgumentException if there is no registered writer for a given type
     * @throws IllegalStateException if any {@code send(...)} method was already called
     */
    <T> CompletionStage<ServerResponse> send(T content);

    /**
     * Send a message as is without any other marshalling. The response is completed when publisher send
     * {@link Flow.Subscriber#onComplete()} method to its subscriber.
     * <p>
     * A single {@link Flow.Subscription Subscriber} subscribes to the provided {@link Flow.Publisher Publisher} during
     * the method execution.
     *
     * <h3>Blocking</h3>
     * The method blocks only during marshalling. It means until {@code registered writer} produce a {@code Publisher} and
     * subscribe HTTP IO implementation on it. If the thread is used for publishing is up to HTTP IO and generated Publisher
     * implementations. Use returned {@link CompletionStage} to monitor and react on finished sending tryProcess.
     *
     * @param content a response content publisher
     * @return a completion stage of the response - completed when response is transferred
     * @throws IllegalStateException if any {@code send(...)} method was already called
     */
    CompletionStage<ServerResponse> send(Flow.Publisher<DataChunk> content);

    /**
     * Sends an empty response. Do nothing if response was already send.
     *
     * @return a completion stage of the response - completed when response is transferred
     */
    CompletionStage<ServerResponse> send();

    /**
     * Registers a content writer for a given type.
     * <p>
     * Registered writer is used to marshal response content of given type to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk response chunks}.
     *
     * @param type     a type of the content. If {@code null} then accepts any type.
     * @param function a writer function
     * @param <T>      a type of the content
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if {@code function} parameter is {@code null}
     */
    <T> ServerResponse registerWriter(Class<T> type, Function<T, Flow.Publisher<DataChunk>> function);

    /**
     * Registers a content writer for a given type and media type.
     * <p>
     * Registered writer is used to marshal response content of given type to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk response chunks}. It is used only if {@code Content-Type} header is compatible with a given
     * content type or if it is {@code null}. If {@code Content-Type} is {@code null} and it is still possible to modify
     * headers (headers were not send yet), the provided content type will be set.
     *
     * @param type        a type of the content. If {@code null} then accepts any type.
     * @param contentType a {@code Content-Type} of the entity
     * @param function    a writer function
     * @param <T>         a type of the content
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if {@code function} parameter is {@code null}
     */
    <T> ServerResponse registerWriter(Class<T> type,
                                      MediaType contentType,
                                      Function<? extends T, Flow.Publisher<DataChunk>> function);

    /**
     * Registers a content writer for all accepted contents.
     * <p>
     * Registered writer is used to marshal response content of given type to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk response chunks}.
     *
     * @param accept   a predicate to test if content is marshallable by the writer. If {@code null} then accepts any type.
     * @param function a writer function
     * @param <T>      a type of the content
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if {@code function} parameter is {@code null}
     */
    <T> ServerResponse registerWriter(Predicate<?> accept, Function<T, Flow.Publisher<DataChunk>> function);

    /**
     * Registers a content writer for all accepted contents.
     * <p>
     * Registered writer is used to marshal response content of given type to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk response chunks}. It is used only if {@code Content-Type} header is compatible with a given
     * content type or if it is {@code null}. If {@code Content-Type} is {@code null} and it is still possible to modify
     * headers (headers were not send yet), the provided content type will be set.
     *
     * @param accept      a predicate to test if content is marshallable by the writer. If {@code null} then accepts any type.
     * @param contentType a {@code Content-Type} of the entity
     * @param function    a writer function
     * @param <T>         a type of the content
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if {@code function} parameter is {@code null}
     */
    <T> ServerResponse registerWriter(Predicate<?> accept,
                                      MediaType contentType,
                                      Function<T, Flow.Publisher<DataChunk>> function);

    /**
     * Registers a provider of the new response content publisher - typically a filter.
     * <p>
     * All response content is always represented by a single {@link Flow.Publisher Publisher}
     * of {@link DataChunk response chunks}. This method can be used to filter or completely replace original publisher by
     * a new one with different contract. For example data coding, logging, filtering, caching, etc.
     * <p>
     * New publisher is created at the moment of content write by any {@link #send(Object) send(...)} method including the empty
     * one.
     * <p>
     * All registered filters are used as a chain from original content {@code Publisher}, first registered to the last
     * registered.
     *
     * @param function a function to map previously registered or original {@code Publisher} to the new one. If returns
     *                 {@code null} then the result will be ignored.
     * @return this instance of {@link ServerResponse}
     * @throws NullPointerException if parameter {@code function} is {@code null}
     */
    ServerResponse registerFilter(Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>> function);

    /**
     * Completion stage is completed when response is completed.
     * <p>
     * It can be used to react on the response completion without invocation of a closing event.
     *
     * @return a completion stage of the response
     */
    CompletionStage<ServerResponse> whenSent();

    /**
     * A unique correlation ID that is associated with this response and its associated request.
     *
     * @return a unique correlation ID associated with this response and its request
     */
    long requestId();
}

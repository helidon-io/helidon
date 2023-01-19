/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpRequest;

/**
 * The request context.
 */
class RequestContext {

    private static final Logger LOGGER = Logger.getLogger(RequestContext.class.getName());

    private final HttpRequestScopedPublisher publisher;
    private final HttpRequest request;
    private final Context scope;
    private final SocketConfiguration socketConfiguration;
    private volatile boolean responseCompleted;
    private volatile boolean emitted;

    RequestContext(HttpRequestScopedPublisher publisher,
                   HttpRequest request,
                   Context scope,
                   SocketConfiguration socketConfiguration) {
        this.publisher = publisher;
        this.request = request;
        this.scope = scope;
        this.socketConfiguration = socketConfiguration;
    }

    Multi<DataChunk> publisher() {
        return Multi.create(publisher)
                .peek(something -> emitted = true);
    }

    HttpRequest request() {
        return request;
    }

    void emit(ByteBuf data) {
        runInScope(() -> publisher.emit(data));
    }

    void fail(Throwable throwable) {
        runInScope(() -> publisher.fail(throwable));
    }

    void complete() {
        runInScope(publisher::complete);
    }

    void runInScope(Runnable runnable) {
        Contexts.runInContext(scope, () -> {
            LOGGER.finest(() -> "Running in context " + scope.id());
            runnable.run();
        });
    }

    Context scope() {
        return scope;
    }

    /**
     * Has been request content stream requested.
     *
     * @return true if requested
     */
    boolean hasRequests() {
        return publisher.hasRequests();
    }

    /**
     * Has there been a request for content.
     *
     * @return {@code true} if data was requested and request was not cancelled
     */
    boolean isDataRequested() {
        return (hasRequests() || hasEmitted()) || requestCancelled();
    }

    boolean hasEmitted() {
        return emitted;
    }

    /**
     * Is request content cancelled.
     *
     * @return true if cancelled
     */
    boolean requestCancelled() {
        return publisher.isCancelled();
    }

    /**
     * Is request content consumed.
     *
     * @return true if consumed
     */
    boolean requestCompleted() {
        return publisher.isCompleted();
    }

    void responseCompleted(boolean responseCompleted) {
        this.responseCompleted = responseCompleted;
    }

    boolean responseCompleted() {
        return responseCompleted;
    }

    SocketConfiguration socketConfiguration() {
        return socketConfiguration;
    }
}

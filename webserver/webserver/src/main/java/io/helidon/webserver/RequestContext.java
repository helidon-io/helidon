/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates. All rights reserved.
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
    private volatile boolean responseCompleted;

    RequestContext(HttpRequestScopedPublisher publisher, HttpRequest request, Context scope) {
        this.publisher = publisher;
        this.request = request;
        this.scope = scope;
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
            LOGGER.finest(() -> String.format("Running in context %s", scope.id()));
            runnable.run();
        });
    }

    boolean hasRequests() {
        return publisher.hasRequests();
    }

    void responseCompleted(boolean responseCompleted) {
        this.responseCompleted = responseCompleted;
    }

    boolean responseCompleted() {
        return responseCompleted;
    }
}

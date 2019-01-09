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

package io.helidon.webserver.netty;

import io.netty.handler.codec.http.HttpRequest;

/**
 * The RequestContext POJO holds single HTTP request associated objects.
 */
class RequestContext {

    private final HttpRequestScopedPublisher publisher;
    private final HttpRequest request;
    private volatile boolean responseCompleted;

    RequestContext(HttpRequestScopedPublisher publisher, HttpRequest request) {
        this.publisher = publisher;
        this.request = request;
    }

    HttpRequestScopedPublisher publisher() {
        return publisher;
    }

    HttpRequest request() {
        return request;
    }

    public void responseCompleted(boolean responseCompleted) {
        this.responseCompleted = responseCompleted;
    }

    public boolean responseCompleted() {
        return responseCompleted;
    }
}

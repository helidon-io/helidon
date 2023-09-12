/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.webserver.ServerLifecycle;

/**
 * Handle a server request and server response.
 * Handlers are used to construct routing.
 */
public interface Handler extends ServerLifecycle {
    /**
     * Create a handler that only runs code and returns {@link io.helidon.http.Status#OK_200}.
     *
     * @param handler runnable to run
     * @return handler
     */
    static Handler create(Runnable handler) {
        return (req, res) -> {
            handler.run();
            res.send();
        };
    }

    /**
     * Create a handler that consumes a {@link ServerRequest} and returns {@link io.helidon.http.Status#OK_200}.
     *
     * @param handler consumer of request
     * @return handler
     */
    static Handler create(Consumer<ServerRequest> handler) {
        return (req, res) -> {
            handler.accept(req);
            res.send();
        };
    }

    /**
     * Create a handler that consumes a {@link ServerRequest} and returns an entity object.
     *
     * @param handler function that gets a request and produces an entity
     * @return handler
     */
    static Handler create(Function<ServerRequest, ?> handler) {
        return (req, res) -> {
            Object response = handler.apply(req);
            res.send(response);
        };
    }

    /**
     * Create a handler that produces an entity.
     *
     * @param handler supplier of entity object
     * @return handler
     */
    static Handler create(Supplier<?> handler) {
        return (req, res) -> {
            Object response = handler.get();
            res.send(response);
        };
    }

    /**
     * Create a handler that consumes typed request entity and produces an entity object.
     *
     * @param type    type of the request entity
     * @param handler function that gets request entity and produces response entity
     * @param <T>     type of the request entity
     * @return handler
     */
    static <T> Handler create(Class<T> type, Function<T, ?> handler) {
        return (req, res) -> {
            Object response = handler.apply(req.content().as(type));
            res.send(response);
        };
    }

    /**
     * Create a handler that consumes typed request entity and sends {@link io.helidon.http.Status#OK_200}.
     *
     * @param type    type of request entity
     * @param handler consumer of request entity
     * @param <T>     type of request entity
     * @return handler
     */
    static <T> Handler create(Class<T> type, Consumer<T> handler) {
        return (req, res) -> {
            handler.accept(req.content().as(type));
            res.send();
        };
    }

    /**
     * Create a handler that consumes type request entity and {@link ServerResponse}.
     *
     * @param type    type of request entity
     * @param handler consumer of typed request entity and server response
     * @param <T>     type of request entity
     * @return handler
     */
    static <T> Handler create(Class<T> type, BiConsumer<T, ServerResponse> handler) {
        return (req, res) -> {
            handler.accept(req.content().as(type), res);
        };
    }

    /**
     * Handle request. This method must not return before the response is completed.
     * If the method does asynchronous operations, it must wait for them to complete before returning.
     *
     * @param req request
     * @param res response
     * @throws java.lang.Exception may throw checked exceptions that are handled by the server, either by error handler, or
     *      by returning an internal server error (default handling)
     */
    void handle(ServerRequest req, ServerResponse res) throws Exception;
}

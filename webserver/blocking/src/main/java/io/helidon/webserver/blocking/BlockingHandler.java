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

import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * A handler for blocking APIs.
 * Use one of the {@link #create(java.util.function.Supplier)} methods to create a blocking handler.
 * Use the {@link #executorService(java.util.concurrent.ExecutorService)} to configure a custom executor service
 * to process incoming requests (must be configured before the first request).
 * Default executor service will use virtual threads if available (project Loom).
 * <p>
 * Handlers that return entity can control exception cases using {@link io.helidon.webserver.HttpException} to
 *  return a specific HTTP status code.
 */
@FunctionalInterface
public interface BlockingHandler extends Handler {
    /**
     * A method handling a blocking request.
     *
     * @param req HTTP request abstraction with blocking methods
     * @param res HTTP response abstraction with blocking methods
     */
    void handle(BlockingRequest req, BlockingResponse res);

    @Override
    default void accept(ServerRequest req, ServerResponse res) {
        ExecutorServiceSupport.executorService()
                .submit(() -> {
                    String originalName = Thread.currentThread().getName();
                    if ("<unnamed>".equals(originalName)) {
                        // virtual thread
                        Thread.currentThread().setName("Virtual: " + req.method().name() + " " + req.path());
                    } else {
                        Thread.currentThread().setName(originalName + ": " + req.method().name() + " " + req.path());
                    }
                    try {
                        handle(BlockingRequestImpl.create(req), BlockingResponseImpl.create(res));
                    } catch (Throwable e) {
                        req.next(e);
                    } finally {
                        Thread.currentThread().setName(originalName);
                    }
                });
    }

    /**
     * Configure executor service to use for processing blocking requests.
     *
     * @param executor executor service to use
     */
    static void executorService(ExecutorService executor) {
        ExecutorServiceSupport.defaultExecutor(executor);
    }

    /**
     * Configure executor service to use for processing blocking requests.
     *
     * @param executor executor service to use
     */
    static void executorService(Supplier<ExecutorService> executor) {
        ExecutorServiceSupport.defaultExecutor(executor);
    }

    /**
     * Create a blocking handler that uses both request and response for processing.
     *
     * @param handler handler to use
     * @return a new blocking handler to register with web server
     */
    static BlockingHandler create(BiConsumer<BlockingRequest, BlockingResponse> handler) {
        return handler::accept;
    }

    /**
     * Create a blocking handler that consumes entity.
     * This handler will use HTTP status code {@link Http.Status#NO_CONTENT_204}.
     *
     * @param requestEntityType type of entity to read (must be supported by one of the readers registered with web server)
     * @param handler consumer of the entity
     * @param <T> type of request entity
     * @return a new blocking handler to register with web server
     */
    static <T> BlockingHandler create(Class<T> requestEntityType, Consumer<T> handler) {
        return (req, res) -> {
            T requestObject = req.content().as(requestEntityType);
            handler.accept(requestObject);
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
        };
    }

    /**
     * Create a blocking handler that consumes entity and returns an entity.
     *
     * @param requestEntityType type of entity to read (must be supported by one of the readers registered with web server)
     * @param handler handler that consumes entity and returns an entity to respond with
     * @param <T> type of request entity
     * @return a new blocking handler to register with web server
     */
    static <T> BlockingHandler create(Class<T> requestEntityType, Function<T, ?> handler) {
        return (req, res) -> {
            T requestObject = req.content().as(requestEntityType);
            Object response = handler.apply(requestObject);
            res.send(response);
        };
    }

    /**
     * Create a blocking handler that consumes full request and returns an entity.
     * @param handler handler that consumes request and returns an entity
     * @return a new blocking handler to register with web server
     */
    static BlockingHandler create(Function<BlockingRequest, ?> handler) {
        return (req, res) -> {
            Object response = handler.apply(req);
            res.send(response);
        };
    }

    /**
     * Create a blocking handler that provides an entity.
     *
     * @param handler handler that supplies response entity
     * @return a new blocking handler to register with web server
     */
    static BlockingHandler create(Supplier<?> handler) {
        return (req, res) -> {
            Object response = handler.get();
            res.send(response);
        };
    }

    /**
     * Create a blocking handler that runs code that does not require any information from request
     * and does not return an entity.
     *
     * @param handler handler that runs on request
     * @return new blocking handler to register with web server
     */
    static BlockingHandler create(Runnable handler) {
        return (req, res) -> {
            handler.run();
            res.send();
        };
    }
}

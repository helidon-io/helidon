/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.Single;

/**
 * The {@link ServerRequest} and {@link ServerResponse} handler.
 * Can be mapped to the routing rules in the {@link Routing}.
 *
 * @see Routing.Builder
 * @see Routing.Rules
 */
@FunctionalInterface
public interface Handler extends BiConsumer<ServerRequest, ServerResponse> {

    /**
     * Handles {@link ServerRequest request} and {@link ServerResponse response} usually called from the {@link Routing}.
     *
     * @param req an HTTP server request.
     * @param res an HTTP server response.
     */
    @Override
    void accept(ServerRequest req, ServerResponse res);

    /**
     * Creates new instance of the {@link Handler} for the entity representing HTTP request content. See {@link ServerRequest}
     * for more details how response can be represented as a java type.
     * <p>
     * Created handler forwards any error created during entity read or conversion to the standard error handling
     * ({@link ServerRequest#next(Throwable)}).
     *
     * @param entityType    a java type of the entity
     * @param entityHandler an entity handler to handle request entity
     * @param <T>           a type of the entity
     * @return new {@code Handler} instance
     */
    static <T> Handler create(Class<T> entityType, EntityHandler<T> entityHandler) {
        return create(entityType, entityHandler, null);
    }

    /**
     * Creates new instance of the {@link Handler} for the entity representing HTTP request content. See {@link ServerRequest}
     * for more details how response can be represented as a java type.
     *
     * @param entityType             a java type of the entity
     * @param entityHandler          an entity handler to handle request entity
     * @param entityReadErrorHandler an error handler to handle state when entity cannot be read or convert to the requested
     *                               type
     * @param <T>                    a type of the entity
     * @return new {@code Handler} instance
     */
    static <T> Handler create(Class<T> entityType,
                              EntityHandler<T> entityHandler,
                              ErrorHandler<Throwable> entityReadErrorHandler) {
        Objects.requireNonNull(entityType, "Parameter 'publisherType' is null!");
        Objects.requireNonNull(entityHandler, "Parameter 'entityHandler' is null!");
        return (req, res) -> {
            Single<? extends T> cs;
            Optional<Context> context = Contexts.context();
            try {
                cs = req.content().as(entityType);
            } catch (Throwable thr) {
                if (entityReadErrorHandler == null) {
                    req.next(thr);
                } else {
                    entityReadErrorHandler.accept(req, res, thr);
                }
                return;
            }
            cs.thenAccept(entity -> {
                context.ifPresentOrElse(theContext -> {
                            Contexts.runInContext(theContext, () -> entityHandler.accept(req, res, entity));
                        }, () -> entityHandler.accept(req, res, entity));

            }).exceptionally(throwable -> {
                if (entityReadErrorHandler == null) {
                    req.next(throwable instanceof CompletionException ? throwable.getCause() : throwable);
                } else {
                    entityReadErrorHandler.accept(req, res, throwable);
                }
                return null;
            });
        };
    }

    /**
     * Handles {@link ServerRequest request}, {@link ServerResponse response} and HTTP request content entity.
     * Used as functional parameter in {@link #create(Class, EntityHandler)} method.
     *
     * @param <T> a type of the content entity
     */
    @FunctionalInterface
    interface EntityHandler<T> {

        /**
         * Functional method handles {@link ServerRequest request}, {@link ServerResponse response} and HTTP request content
         * entity.
         *
         * @param req    an HTTP request
         * @param res    an HTTP response
         * @param entity an entity representing the HTTP request content
         */
        void accept(ServerRequest req, ServerResponse res, T entity);

    }

}

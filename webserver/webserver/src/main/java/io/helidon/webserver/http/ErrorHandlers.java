/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.net.SocketException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.helidon.http.BadRequestException;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.InternalServerException;
import io.helidon.http.RequestException;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;

/**
 * Http routing Error handlers.
 */
public final class ErrorHandlers {
    private static final System.Logger LOGGER = System.getLogger(ErrorHandlers.class.getName());
    private final IdentityHashMap<Class<? extends Throwable>, ErrorHandler<?>> errorHandlers;

    private ErrorHandlers(IdentityHashMap<Class<? extends Throwable>, ErrorHandler<?>> errorHandlers) {
        this.errorHandlers = errorHandlers;
    }

    /**
     * Create error handlers.
     *
     * @param errorHandlers map of type to error handler
     * @return new error handlers
     */
    public static ErrorHandlers create(Map<Class<? extends Throwable>, ErrorHandler<?>> errorHandlers) {
        return new ErrorHandlers(new IdentityHashMap<>(errorHandlers));
    }

    @Override
    public String toString() {
        return "ErrorHandlers for " + errorHandlers.keySet();
    }

    /**
     * Run a task and handle the errors, if any. Uses error handlers configured on routing.
     * Correctly handles all expected (and unexpected) exceptions that can happen in filters and routes.
     *
     * @param ctx      connection context
     * @param request  HTTP server request
     * @param response HTTP server response
     * @param task     task to execute
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void runWithErrorHandling(ConnectionContext ctx,
                                     RoutingRequest request,
                                     RoutingResponse response,
                                     Callable<Void> task) {
        try {
            task.call();
            if (response.hasEntity()) {
                response.commit();
            }
        } catch (CloseConnectionException e) {
            // these errors must "bubble up"
            throw e;
        } catch (RequestException e) {
            handleRequestException(ctx, request, response, e);
        } catch (BadRequestException e) {
            // bad request exception MUST be handled by direct handlers
            handleRequestException(ctx, request, response, RequestException.builder()
                    .message(e.getMessage())
                    .cause(e)
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .status(e.status())
                    .setKeepAlive(e.keepAlive())
                    .build());
        } catch (InternalServerException e) {
            // this is the place error handling must be done
            // check if error handler exists for cause - if so, use it
            ErrorHandler errorHandler = null;
            Throwable exception = null;

            if (e.getCause() != null) {
                var maybeEh = errorHandler(e.getCause().getClass());
                if (maybeEh.isPresent()) {
                    errorHandler = maybeEh.get();
                    exception = e.getCause();
                }
            }

            if (errorHandler == null) {
                errorHandler = errorHandler(e.getClass()).orElse(null);
                exception = e;
            }

            if (errorHandler == null) {
                unhandledError(ctx, request, response, exception);
            } else {
                handleError(ctx, request, response, exception, errorHandler);
            }
        } catch (RuntimeException e) {
            handleError(ctx, request, response, e);
        } catch (Throwable e) {
            if (e.getCause() instanceof SocketException se) {
                throw new ServerConnectionException("SocketException during routing", se);
            }
            handleError(ctx, request, response, e);
        }
    }

    @SuppressWarnings("unchecked")
    <T extends Throwable> Optional<ErrorHandler<T>> errorHandler(Class<T> exceptionClass) {
        // then look for error handlers that handle supertypes of this exception from lower to higher
        Class<? extends Throwable> throwableClass = exceptionClass;
        while (true) {
            // first look for exact match
            ErrorHandler<?> errorHandler = errorHandlers.get(throwableClass);
            if (errorHandler != null) {
                return Optional.of((ErrorHandler<T>) errorHandler);
            }
            if (!Throwable.class.isAssignableFrom(throwableClass)) {
                return Optional.empty();
            }
            if (throwableClass == Throwable.class) {
                return Optional.empty();
            }
            throwableClass = (Class<? extends Throwable>) throwableClass.getSuperclass();
        }
    }

    private void handleRequestException(ConnectionContext ctx,
                                        ServerRequest request,
                                        RoutingResponse response,
                                        RequestException e) {
        if (!response.reset()) {
            ctx.log(LOGGER, System.Logger.Level.WARNING, "Request failed: " + request.prologue()
                    + ", cannot send error response, as response already sent", e);
            throw new CloseConnectionException(
                    "Cannot send response of an error handler, status and headers already written");
        }
        boolean keepAlive = e.keepAlive();
        if (keepAlive && !request.content().consumed()) {
            // there is a chance, that the 100-Continue was already sent! In such a case, we MUST consume entity
            if (request.headers().contains(HeaderValues.EXPECT_100) && !request.continueSent()) {
                // No content is coming, reset connection
                request.reset();
            } else {
                try {
                    // attempt to consume the request entity (only when keeping the connection alive)
                    request.content().consume();
                } catch (Exception ignored) {
                    keepAlive = request.content().consumed();
                }
            }
        }
        ctx.listenerContext()
                .directHandlers()
                .handle(e, response, keepAlive);

        response.commit();
    }

    @SuppressWarnings("unchecked")
    private void handleError(ConnectionContext ctx, RoutingRequest request, RoutingResponse response, Throwable e) {
        errorHandler(e.getClass())
                .ifPresentOrElse(it -> handleError(ctx, request, response, e, (ErrorHandler<Throwable>) it),
                                 () -> unhandledError(ctx, request, response, e));
    }

    private void unhandledError(ConnectionContext ctx, ServerRequest request, RoutingResponse response, Throwable e) {
        if (e instanceof HttpException httpException) {
            handleRequestException(ctx, request, response, RequestException.builder()
                    .cause(e)
                    .type(DirectHandler.EventType.OTHER)
                    .message(e.getMessage())
                    .status(httpException.status())
                    .setKeepAlive(httpException.keepAlive())
                    .request(DirectTransportRequest.create(request.prologue(), request.headers()))
                    .update(it -> httpException.headers().forEach(it::header))
                    .build());
        } else {
            // to be handled by error handler
            handleRequestException(ctx, request, response, RequestException.builder()
                    .cause(e)
                    .type(DirectHandler.EventType.INTERNAL_ERROR)
                    .message(e.getMessage())
                    .request(DirectTransportRequest.create(request.prologue(), request.headers()))
                    .build());
        }
    }

    private void handleError(ConnectionContext ctx,
                             RoutingRequest request,
                             RoutingResponse response,
                             Throwable e,
                             ErrorHandler<Throwable> it) {
        if (!response.reset()) {
            ctx.log(LOGGER, System.Logger.Level.WARNING, "Unable to reset response for error handler.");
            throw new CloseConnectionException(
                    "Cannot send response of a simple handler, status and headers already written", e);
        }
        try {
            it.handle(request, response, e);
            response.commit();
            if (!response.isSent()) {
                ctx.log(LOGGER, System.Logger.Level.TRACE, "Exception not handled.", e);
                unhandledError(ctx, request, response, e);
            }
        } catch (Exception ex) {
            ctx.log(LOGGER, System.Logger.Level.TRACE, "Failed to handle exception.", ex);
            unhandledError(ctx, request, response, e);
        }
    }
}

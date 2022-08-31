/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import java.io.UncheckedIOException;
import java.net.SocketException;

import io.helidon.common.http.BadRequestException;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.HttpException;
import io.helidon.common.http.InternalServerException;
import io.helidon.common.http.RequestException;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;

/**
 * Http routing Error handlers.
 */
public final class ErrorHandlers {
    private static final System.Logger LOGGER = System.getLogger(ErrorHandlers.class.getName());

    ErrorHandlers() {
    }

    /**
     * Run a task and handle the errors, if any. Uses error handlers configured on routing.
     * Correctly handles all expected (and unexpected) exceptions that can happen in filters and routes.
     *
     * @param ctx connection context
     * @param request HTTP server request
     * @param response HTTP server response
     * @param task task to execute
     */
    public void runWithErrorHandling(ConnectionContext ctx, ServerRequest request, ServerResponse response, Executable task) {
        try {
            task.execute();
        } catch (CloseConnectionException | UncheckedIOException e) {
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
            if (hasErrorHandler(e.getCause())) {
                handleError(ctx, request, response, e.getCause());
            } else {
                handleError(ctx, request, response, e);
            }
        } catch (HttpException e) {
            handleError(ctx, request, response, e);
        } catch (RuntimeException e) {
            handleError(ctx, request, response, e);
        } catch (Exception e) {
            if (e.getCause() instanceof SocketException se) {
                throw new UncheckedIOException(se);
            }
            handleError(ctx, request, response, e);
        }
    }

    private void handleRequestException(ConnectionContext ctx, ServerRequest request, ServerResponse response, RequestException e) {
        if (response.isSent()) {
            ctx.log(LOGGER, System.Logger.Level.WARNING, "Request failed: " + request.prologue()
                    + ", cannot send error response, as response already sent", e);
        }
        boolean keepAlive = e.keepAlive();
        if (keepAlive && !request.content().consumed()) {
            try {
                // attempt to consume the request entity (only when keeping the connection alive)
                request.content().consume();
            } catch (Exception ignored) {
                keepAlive = request.content().consumed();
            }
        }
        ctx.directHandlers().handle(e, response, keepAlive);
    }

    private boolean hasErrorHandler(Throwable cause) {
        // TODO needs implementation (separate issue)
        return true;
    }

    private void handleError(ConnectionContext ctx, ServerRequest request, ServerResponse response, Throwable e) {
        // to be handled by error handler
        handleRequestException(ctx, request, response, RequestException.builder()
                .cause(e)
                .type(DirectHandler.EventType.INTERNAL_ERROR)
                .message(e.getMessage())
                .request(DirectTransportRequest.create(request.prologue(), request.headers()))
                .build());
    }

    private void handleError(ConnectionContext ctx, ServerRequest request, ServerResponse response, HttpException e) {
        // to be handled by error handler
        handleRequestException(ctx, request, response, RequestException.builder()
                .cause(e)
                .type(DirectHandler.EventType.OTHER)
                .message(e.getMessage())
                .status(e.status())
                .setKeepAlive(e.keepAlive())
                .request(DirectTransportRequest.create(request.prologue(), request.headers()))
                .build());
    }
}

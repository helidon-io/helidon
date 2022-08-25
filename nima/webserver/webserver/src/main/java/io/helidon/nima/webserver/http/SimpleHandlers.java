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

import java.util.EnumMap;
import java.util.Map;

import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.Http;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.HtmlEncoder;
import io.helidon.nima.webserver.http.SimpleHandler.EventType;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Configured handlers for expected (and internal) exceptions.
 */
public class SimpleHandlers {
    private static final System.Logger LOGGER = System.getLogger(SimpleHandlers.class.getName());

    private final Map<EventType, SimpleHandler> handlers;

    private SimpleHandlers(Map<EventType, SimpleHandler> handlers) {
        this.handlers = new EnumMap<>(handlers);
    }

    /**
     * New builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get handler for the event type.
     * If no custom handler is defined, the default handler will be returned.
     *
     * @param eventType event type
     * @return handler to use
     */
    public SimpleHandler handler(EventType eventType) {
        return handlers.get(eventType);
    }

    /**
     * Handle an HTTP Exception that occurred when request and response is available.
     *
     * @param httpException exception to handle
     * @param res           response
     */
    public void handle(HttpException httpException, ServerResponse res) {
        SimpleHandler.SimpleResponse response = handler(httpException.eventType()).handle(
                httpException.request(),
                httpException.eventType(),
                httpException.status(),
                httpException.responseHeaders(),
                httpException);

        Http.Status usedStatus;

        res.status(response.status());
        response.headers()
                .forEach(res::header);
        if (!httpException.keepAlive()) {
            res.header(Http.HeaderValues.CONNECTION_CLOSE);
        }

        if (res.isSent()) {
            throw new CloseConnectionException(
                    "Cannot send response of a simple handler, status and headers already written");
        }

        try {
            response.message().ifPresentOrElse(res::send, res::send);
        } catch (IllegalStateException ex) {
            // failed to send - probably output stream was already obtained and used, so status is written
            // we can only close the connection now
            res.streamResult(response.message().map(String::new).orElseGet(() -> httpException.getCause().getMessage()));
            throw new CloseConnectionException(
                    "Cannot send response of a simple handler, status and headers already written",
                    ex);
        }

        usedStatus = response.status();

        if (usedStatus == Http.Status.INTERNAL_SERVER_ERROR_500) {
            LOGGER.log(WARNING, "Internal server error", httpException);
        }
    }

    /**
     * Fluent API builder for {@link SimpleHandlers}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, SimpleHandlers> {
        private final Map<EventType, SimpleHandler> handlers = new EnumMap<>(EventType.class);
        private final SimpleHandler defaultHandler = new DefaultHandler();

        private Builder() {
        }

        @Override
        public SimpleHandlers build() {
            for (EventType value : EventType.values()) {
                handlers.putIfAbsent(value, defaultHandler);
            }
            return new SimpleHandlers(handlers);
        }

        /**
         * Add a handler.
         *
         * @param eventType event type to handle
         * @param handler   handler to handle that type
         * @return updated builder
         */
        public Builder addHandler(EventType eventType, SimpleHandler handler) {
            handlers.put(eventType, handler);
            return this;
        }
    }

    private static class DefaultHandler implements SimpleHandler {
        @Override
        public SimpleResponse handle(SimpleRequest request,
                                     EventType eventType,
                                     Http.Status defaultStatus,
                                     HeadersServerResponse headers,
                                     String message) {
            return SimpleResponse.builder()
                    .status(defaultStatus)
                    .headers(headers)
                    .update(it -> {
                        if (!message.isEmpty()) {
                            it.message(HtmlEncoder.encode(message));
                        }
                    })
                    .build();
        }
    }
}

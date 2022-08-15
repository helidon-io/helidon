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

import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.nima.webserver.SimpleHandler;

/**
 * HTTP exception. This allows custom handlers to be used for different even types.
 */
public class HttpException extends RuntimeException {
    private final SimpleHandler.EventType eventType;
    private final Http.Status status;
    private final SimpleHandler.SimpleRequest simpleRequest;
    private final ServerResponse fullResponse;
    private final boolean keepAlive;

    private HttpException(Builder builder) {
        super(builder.message, builder.cause);
        this.eventType = builder.type;
        this.status = builder.status;
        this.simpleRequest = builder.request;
        this.fullResponse = builder.fullResponse;
        this.keepAlive = builder.keepAlive;
    }

    /**
     * Builder to set up a new HTTP exception.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Configured HTTP status.
     *
     * @return status
     */
    public Http.Status status() {
        return status;
    }

    /**
     * Event type of this exception.
     *
     * @return event type
     */
    public SimpleHandler.EventType eventType() {
        return eventType;
    }

    /**
     * Simple request with as much information as is available.
     *
     * @return request
     */
    public SimpleHandler.SimpleRequest request() {
        return simpleRequest;
    }

    /**
     * Routing response (if available). This is used to correctly send response through this vehicle to handle
     * post-send events.
     *
     * @return routing response if available
     */
    public Optional<ServerResponse> fullResponse() {
        return Optional.ofNullable(fullResponse);
    }

    /**
     * Whether to attempt to keep connection alive.
     *
     * @return whether to keep connection alive
     */
    public boolean keepAlive() {
        return keepAlive;
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.webserver.http.HttpException}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, HttpException> {
        private String message;
        private Throwable cause;
        private SimpleHandler.SimpleRequest request;
        private SimpleHandler.EventType type;
        private Http.Status status;
        private ServerResponse fullResponse;
        private Boolean keepAlive;

        private Builder() {
        }

        @Override
        public HttpException build() {
            if (message == null) {
                message = "";
            }
            if (request == null) {
                request = SimpleHandler.SimpleRequest.empty();
            }
            if (type == null) {
                type(SimpleHandler.EventType.INTERNAL_ERROR);
            }
            return new HttpException(this);
        }

        /**
         * Descriptive error message.
         *
         * @param message message
         * @return updated builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Cause of the exception.
         *
         * @param cause cause
         * @return updated builder
         */
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        /**
         * Routing request.
         *
         * @param request request to obtain information from
         * @return updated builder
         */
        public Builder request(ServerRequest request) {
            this.request = HttpSimpleRequest.create(request.prologue(), request.headers());
            return this;
        }

        /**
         * Routing response to be used to handle response from simple handler.
         *
         * @param response response to use
         * @return updated builder
         */
        public Builder response(ServerResponse response) {
            this.fullResponse = response;
            return this;
        }

        /**
         * Simple request with as much information as is available.
         *
         * @param request request to use
         * @return updated builder
         */
        public Builder request(SimpleHandler.SimpleRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Event type of this exception.
         *
         * @param type type to use
         * @return updated builder
         */
        public Builder type(SimpleHandler.EventType type) {
            this.type = type;
            if (status == null) {
                status = type.defaultStatus();
            }
            if (keepAlive == null) {
                keepAlive = type.keepAlive();
            }
            return this;
        }

        /**
         * Http status to use. This will override default status from
         * {@link SimpleHandler.EventType#defaultStatus()}.
         *
         * @param status status to use
         * @return updated builder
         */
        public Builder status(Http.Status status) {
            this.status = status;
            return this;
        }

        /**
         * Override default keep alive for this exception.
         *
         * @param keepAlive whether to keep connection alive
         * @return updated builderw
         */
        public Builder setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }
    }
}

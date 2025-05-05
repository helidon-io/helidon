/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

/**
 * Exception that will be handled by {@link DirectHandler}, unless server request and server response
 * are already available, in which case it would be handled by appropriate error handler of routing.
 * This exception is not used by clients.
 */
public class RequestException extends RuntimeException {
    /**
     * Type of this event.
     */
    private final DirectHandler.EventType eventType;
    /**
     * HTTP status to return.
     */
    private final Status status;
    /**
     * Request as far as it could have been parsed.
     */
    private final DirectHandler.TransportRequest transportRequest;
    /**
     * Whether the connection can be kept alive.
     */
    private final boolean keepAlive;
    /**
     * Header to return with the response.
     */
    private final ServerResponseHeaders responseHeaders;
    /**
     * Whether the message is safe to be shown in logs and messages.
     */
    private final boolean safeMessage;

    /**
     * A new exception with a predefined status, even type.
     * Additional information may be added to simplify handling.
     *
     * @param builder builder with details to create this instance
     */
    protected RequestException(Builder builder) {
        super(builder.message, builder.cause);
        this.eventType = builder.type;
        this.status = builder.status;
        this.transportRequest = builder.request;
        this.keepAlive = builder.keepAlive;
        this.responseHeaders = builder.responseHeaders;
        this.safeMessage = builder.safeMessage;
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
    public Status status() {
        return status;
    }

    /**
     * Event type of this exception.
     *
     * @return event type
     */
    public DirectHandler.EventType eventType() {
        return eventType;
    }

    /**
     * Transport request with as much information as is available.
     *
     * @return request
     */
    public DirectHandler.TransportRequest request() {
        return transportRequest;
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
     * Response headers that should be added to response.
     *
     * @return response headers
     */
    public ServerResponseHeaders responseHeaders() {
        return responseHeaders;
    }

    /**
     * Safe message flag used to control which messages can be sent as
     * part of a response and which should only be logged by the server.
     *
     * @return safe message flag
     */
    public boolean safeMessage() {
        return safeMessage;
    }

    /**
     * Fluent API builder for {@link RequestException}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, RequestException> {
        private String message;
        private Throwable cause;
        private DirectHandler.TransportRequest request;
        private DirectHandler.EventType type;
        private Status status;
        private Boolean keepAlive;
        private final ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        private boolean safeMessage = true;

        private Builder() {
        }

        @Override
        public RequestException build() {
            if (message == null) {
                message = "";
            }
            if (request == null) {
                request = DirectHandler.TransportRequest.empty();
            }
            if (type == null) {
                type(DirectHandler.EventType.INTERNAL_ERROR);
            }
            return new RequestException(this);
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
         * Transport request with as much information as is available.
         *
         * @param request request to use
         * @return updated builder
         */
        public Builder request(DirectHandler.TransportRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Event type of this exception.
         *
         * @param type type to use
         * @return updated builder
         */
        public Builder type(DirectHandler.EventType type) {
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
         * {@link io.helidon.http.DirectHandler.EventType#defaultStatus()}.
         *
         * @param status status to use
         * @return updated builder
         */
        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        /**
         * Override default keep alive for this exception.
         *
         * @param keepAlive whether to keep connection alive
         * @return updated builder
         */
        public Builder setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * Response header to be added to error response.
         *
         * @param header header to add
         * @return updated builder
         */
        public Builder header(Header header) {
            this.responseHeaders.set(header);
            return this;
        }

        /**
         * Safe message flag that indicates if it safe to return message
         * as part of the response. Defaults to {@code true}.
         *
         * @param safeMessage whether is safe to return message
         * @return updated builder
         */
        public Builder safeMessage(boolean safeMessage) {
            this.safeMessage = safeMessage;
            return this;
        }
    }
}

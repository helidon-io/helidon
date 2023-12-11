/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A handler that is invoked when a response is sent outside of routing.
 * See {@link DirectHandler.EventType} to see which types
 * of events are covered by this handler.
 * Direct handlers can be used both with blocking and reactive servers in Helidon.
 */
@FunctionalInterface
public interface DirectHandler {

    /**
     * Default handler will HTML encode the message (if any),
     * use the default status code for the event type, and copy all headers configured.
     *
     * @return default direct handler
     */
    static DirectHandler defaultHandler() {
        return DirectHandlerDefault.INSTANCE;
    }

    /**
     * Handler of responses that bypass router.
     * <p>
     * This method should be used to return custom status, header and possible entity.
     * If there is a need to handle more details, please redirect the client to a proper endpoint to handle them.
     * This method shall not send an unsafe message back as an entity to avoid potential data leaks.
     *
     * @param request request as received with as much known information as possible
     * @param eventType type of the event
     * @param defaultStatus default status expected to be returned
     * @param responseHeaders headers to be added to response
     * @param thrown throwable caught as part of processing with possible additional details about the reason of failure
     * @return response to use to return to original request
     */
    default TransportResponse handle(TransportRequest request,
                                     EventType eventType,
                                     Status defaultStatus,
                                     ServerResponseHeaders responseHeaders,
                                     Throwable thrown) {
        return handle(request, eventType, defaultStatus, responseHeaders, thrown, null);
    }

    /**
     * Handler of responses that bypass router.
     * <p>
     * This method should be used to return custom status, header and possible entity.
     * If there is a need to handle more details, please redirect the client to a proper endpoint to handle them.
     * This method shall not send an unsafe message back as an entity to avoid potential data leaks.
     *
     * @param request request as received with as much known information as possible
     * @param eventType type of the event
     * @param defaultStatus default status expected to be returned
     * @param responseHeaders headers to be added to response
     * @param thrown throwable caught as part of processing with possible additional details about the reason of failure
     * @param logger Possibly null logger to use for unsafe messages
     * @return response to use to return to original request
     */
    default TransportResponse handle(TransportRequest request,
                                     EventType eventType,
                                     Status defaultStatus,
                                     ServerResponseHeaders responseHeaders,
                                     Throwable thrown,
                                     System.Logger logger) {
        if (thrown instanceof RequestException re) {
            if (re.safeMessage()) {
                return handle(request, eventType, defaultStatus, responseHeaders, thrown.getMessage());
            } else {
                if (logger != null) {
                    logger.log(Level.DEBUG, thrown);
                }
                return handle(request, eventType, defaultStatus, responseHeaders,
                        "Bad request, see server log for more information");
            }
        }
        return handle(request, eventType, defaultStatus, responseHeaders, thrown.getMessage());
    }

    /**
     * Handler of responses that bypass routing.
     * <p>
     * This method should be used to return custom status, header and possible entity.
     * If there is a need to handle more details, please redirect the client to a proper endpoint to handle them.
     *
     * @param request request as received with as much known information as possible
     * @param eventType type of the event
     * @param defaultStatus default status expected to be returned
     * @param responseHeaders headers to be added to response
     * @param message informative message for cases that are not triggered by an exception, by default this will be called
     *                      also
     *                      for exceptional cases with the exception message
     * @return response to use to return to original request
     */
    TransportResponse handle(TransportRequest request,
                             EventType eventType,
                             Status defaultStatus,
                             ServerResponseHeaders responseHeaders,
                             String message);

    /**
     * Request information.
     * Note that the information may not be according to specification, as this marks a bad request (by definition).
     */
    interface TransportRequest {
        /**
         * Create an empty transport request.
         * This is usually used when an error occurs before we could parse request information.
         *
         * @return empty transport request
         */
        static TransportRequest empty() {
            return DirectHandlerEmptyRequest.INSTANCE;
        }

        /**
         * Protocol version (either from actual request, or guessed).
         *
         * @return protocol version
         */
        String protocolVersion();

        /**
         * HTTP method.
         *
         * @return method
         */
        String method();

        /**
         * Requested path, if found in request.
         *
         * @return uri or an empty string
         */
        String path();

        /**
         * Headers, if found in request.
         *
         * @return headers or an empty map
         */
        ServerRequestHeaders headers();
    }

    /**
     * Types of events that can be triggered outside of router
     * that immediately return a response.
     * Each event type has a default status and whether {@code Connection: keep-alive} should be maintained.
     */
    enum EventType {
        /**
         * Bad request, such as invalid path, header.
         */
        BAD_REQUEST(Status.BAD_REQUEST_400, false),
        /**
         * Payload is bigger than the configured maximal size.
         */
        PAYLOAD_TOO_LARGE(Status.REQUEST_ENTITY_TOO_LARGE_413, false),
        /**
         * Forbidden, such as when CORS forbids this request.
         */
        FORBIDDEN(Status.FORBIDDEN_403, true),
        /**
         * Internal server error.
         */
        INTERNAL_ERROR(Status.INTERNAL_SERVER_ERROR_500, true),
        /**
         * Other type, please specify expected status code.
         */
        OTHER(Status.INTERNAL_SERVER_ERROR_500, true);

        private final Status defaultStatus;
        private final boolean keepAlive;

        EventType(Status defaultStatus, boolean keepAlive) {
            this.defaultStatus = defaultStatus;
            this.keepAlive = keepAlive;
        }

        /**
         * Default status of this event type.
         *
         * @return status
         */
        public Status defaultStatus() {
            return defaultStatus;
        }

        /**
         * Whether keep alive should be maintained for this event type.
         *
         * @return whether to keep connectino alive
         */
        public boolean keepAlive() {
            return keepAlive;
        }
    }

    /**
     * Response to correctly reply to the original client.
     */
    class TransportResponse {
        private final Status status;
        private final ServerResponseHeaders headers;
        private final byte[] entity;
        private final boolean keepAlive;

        private TransportResponse(Builder builder) {
            this.status = builder.status;
            this.headers = builder.headers;
            this.entity = builder.entity;
            this.keepAlive = builder.keepAlive;
        }

        /**
         * A builder to set up a custom response.
         *
         * @return builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Configured status.
         *
         * @return status
         */
        public Status status() {
            return status;
        }

        /**
         * Configured headers.
         *
         * @return headers
         */
        public ServerResponseHeaders headers() {
            return headers;
        }

        /**
         * Configured message.
         *
         * @return mesage bytes or empty if no message is configured
         */
        public Optional<byte[]> entity() {
            return Optional.ofNullable(entity);
        }

        /**
         * Whether the connection should use keep alive.
         *
         * @return keep alive
         */
        public boolean keepAlive() {
            return keepAlive;
        }

        /**
         * Fluent API builder for {@link DirectHandler.TransportResponse}.
         */
        public static class Builder implements io.helidon.common.Builder<Builder, TransportResponse> {
            private Status status = Status.BAD_REQUEST_400;
            private byte[] entity;
            private ServerResponseHeaders headers = ServerResponseHeaders.create();
            private boolean keepAlive = true;

            private Builder() {
            }

            @Override
            public TransportResponse build() {
                return new TransportResponse(this);
            }

            /**
             * Custom status.
             *
             * @param status status to use, default is bad request
             * @return updated builder
             */
            public Builder status(Status status) {
                this.status = status;
                return this;
            }

            /**
             * Set headers.
             *
             * @param headers headers to use
             * @return updated builder
             */
            public Builder headers(ServerResponseHeaders headers) {
                this.headers = headers;
                return this;
            }

            /**
             * Set a header (if exists, it would be replaced).
             * Keep alive header is ignored, please use {@link #keepAlive(boolean)}.
             *
             * @param name name of the header
             * @param values value of the header
             * @return updated builder
             */
            public Builder header(HeaderName name, String... values) {
                this.headers.set(name, List.of(values));
                return this;
            }

            /**
             * Set a header (if exists, it would be replaced).
             * Keep alive header is ignored, please use {@link #keepAlive(boolean)}.
             *
             * @param header header value
             * @return updated builder
             */
            public Builder header(Header header) {
                this.headers.add(header);
                return this;
            }

            /**
             * Configure keep alive.
             *
             * @param keepAlive whether to keep alive
             * @return updated builder
             */
            public Builder keepAlive(boolean keepAlive) {
                this.keepAlive = keepAlive;
                return this;
            }

            /**
             * Custom entity. Uses the content, reads it as {@code UTF-8}, configures
             * {@code Content-Length} header, configures {@code Content-Type} header to {@code text/plain}.
             * <p>
             * Use {@link #entity(byte[])} for custom encoding.
             * <p>
             * Note that this method does not do any escaping (such as HTML encoding), make sure the entity is safe.
             *
             * @param entity response entity
             * @return updated builder
             */
            public Builder entity(String entity) {
                this.headers.setIfAbsent(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
                return entity(entity.getBytes(StandardCharsets.UTF_8));
            }

            /**
             * Custom entity. Uses the content, configures
             * {@code Content-Length} header.
             * <p>
             * Use {@link #entity(String)} for simple text messages.
             *
             * @param entity response entity
             * @return updated builder
             */
            public Builder entity(byte[] entity) {
                this.entity = Arrays.copyOf(entity, entity.length);
                if (this.entity.length == 0) {
                    this.headers.remove(HeaderNames.CONTENT_LENGTH);
                } else {
                    header(HeaderNames.CONTENT_LENGTH, String.valueOf(entity.length));
                }
                return this;
            }
        }
    }
}

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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderName;

/**
 * A handler that is invoked when a response is sent outside of router.
 * See {@link SimpleHandler.EventType} to see which types
 * of events are covered by this handler.
 */
@FunctionalInterface
public interface SimpleHandler {
    /**
     * Handler of responses that bypass router.
     * <p>
     * This method should be used to return custom status, header and possible entity.
     * If there is a need to handle more details, please redirect the client to a proper endpoint to handle them.
     *
     * @param request       request as received with as much known information as possible
     * @param eventType     type of the event
     * @param defaultStatus default status expected to be returned
     * @param responseHeaders headers to be added to response
     * @param thrown        throwable caught as part of processing with possible additional details about the reason of failure
     * @return response to use to return to original request
     */
    default SimpleResponse handle(SimpleRequest request,
                                  EventType eventType,
                                  Http.Status defaultStatus,
                                  HeadersServerResponse responseHeaders,
                                  Throwable thrown) {
        return handle(request, eventType, defaultStatus, responseHeaders, thrown.getMessage());
    }

    /**
     * Handler of responses that bypass router.
     * <p>
     * This method should be used to return custom status, header and possible entity.
     * If there is a need to handle more details, please redirect the client to a proper endpoint to handle them.
     *
     * @param request       request as received with as much known information as possible
     * @param eventType     type of the event
     * @param defaultStatus default status expected to be returned
     * @param responseHeaders headers to be added to response
     * @param message       informative message for cases that are not triggered by an exception, by default this will be called
     *                      also
     *                      for exceptional cases with the exception message
     * @return response to use to return to original request
     */
    SimpleResponse handle(SimpleRequest request,
                          EventType eventType,
                          Http.Status defaultStatus,
                          HeadersServerResponse responseHeaders,
                          String message);

    /**
     * Types of events that can be triggered outside of router
     * that immediately return a response.
     * Each event type has a default status and whether {@code Connection: keep-alive} should be maintained.
     */
    enum EventType {
        /**
         * Bad request, such as invalid path, header.
         */
        BAD_REQUEST(Http.Status.BAD_REQUEST_400, false),
        /**
         * Payload is bigger than the configured maximal size.
         */
        PAYLOAD_TOO_LARGE(Http.Status.REQUEST_ENTITY_TOO_LARGE_413, false),
        /**
         * Forbidden, such as when CORS forbids this request.
         */
        FORBIDDEN(Http.Status.FORBIDDEN_403, true),
        /**
         * Internal server error.
         */
        INTERNAL_ERROR(Http.Status.INTERNAL_SERVER_ERROR_500, true),
        /**
         * No route was found for the request.
         */
        NOT_FOUND(Http.Status.NOT_FOUND_404, true),
        /**
         * Other type, please specify expected status code.
         */
        OTHER(Http.Status.INTERNAL_SERVER_ERROR_500, true);

        private final Http.Status defaultStatus;
        private final boolean keepAlive;

        EventType(Http.Status defaultStatus, boolean keepAlive) {
            this.defaultStatus = defaultStatus;
            this.keepAlive = keepAlive;
        }

        /**
         * Default status of this event type.
         *
         * @return status
         */
        public Http.Status defaultStatus() {
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
     * Request information.
     * Note that the information may not be according to specification, as this marks a bad request (by definition).
     */
    interface SimpleRequest {
        /**
         * Empty request, for cases where no information is available.
         *
         * @return empty simple request
         */
        static SimpleRequest empty() {
            return new SimpleRequest() {
                @Override
                public String protocolVersion() {
                    return "";
                }

                @Override
                public String method() {
                    return "";
                }

                @Override
                public String path() {
                    return "";
                }

                @Override
                public Map<String, List<String>> headers() {
                    return Map.of();
                }
            };
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
         * Requested URI, if found in request.
         *
         * @return uri or an empty string
         */
        String path();

        /**
         * Headers, if found in request.
         *
         * @return headers or an empty map
         */
        Map<String, List<String>> headers();
    }

    /**
     * Response to correctly reply to the original client.
     */
    class SimpleResponse {
        private final Http.Status status;
        private final byte[] message;
        private final HeadersServerResponse headers;
        private final boolean keepAlive;

        private SimpleResponse(Builder builder) {
            this.status = builder.status;
            this.message = builder.message;
            this.headers = builder.headers;
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
        public Http.Status status() {
            return status;
        }

        /**
         * Configured headers.
         *
         * @return headers
         */
        public HeadersServerResponse headers() {
            return headers;
        }

        /**
         * Configured message.
         *
         * @return mesage bytes or empty if no message is configured
         */
        public Optional<byte[]> message() {
            return Optional.ofNullable(message);
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
         * Fluent API builder for {@link SimpleHandler.SimpleResponse}.
         */
        public static class Builder implements io.helidon.common.Builder<Builder, SimpleResponse> {
            private Http.Status status = Http.Status.OK_200;
            private byte[] message = BufferData.EMPTY_BYTES;
            private HeadersServerResponse headers = HeadersServerResponse.create();
            private boolean keepAlive = true;

            private Builder() {
            }

            @Override
            public SimpleResponse build() {
                return new SimpleResponse(this);
            }

            /**
             * Custom status.
             *
             * @param status status to use, default is bad request
             * @return updated builder
             */
            public Builder status(Http.Status status) {
                this.status = status;
                return this;
            }

            /**
             * Set headers.
             *
             * @param headers headers to use
             * @return updated builder
             */
            public Builder headers(HeadersServerResponse headers) {
                this.headers = headers;
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
             * Custom entity. Uses the content, encodes it for HTML, reads it as {@code UTF-8}, configures
             * {@code Content-Length} header, configures {@code Content-Type} header to {@code text/plain}.
             * <p>
             * Use {@link #message(byte[])} for custom encoding.
             *
             * @param message response entity
             * @return updated builder
             */
            public Builder message(String message) {
                this.headers.setIfAbsent(Http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
                return message(message.getBytes(StandardCharsets.UTF_8));
            }

            /**
             * Custom entity. Uses the content, configures
             * {@code Content-Length} header.
             * <p>
             * Use {@link #message(String)} for simple text messages.
             *
             * @param entity response entity
             * @return updated builder
             */
            public Builder message(byte[] entity) {
                this.message = entity;
                return this;
            }

            /**
             * Configure an additional header.
             *
             * @param headerName  header name
             * @param headerValue header value
             * @return updated builder
             */
            public Builder header(HeaderName headerName, String headerValue) {
                this.headers.add(headerName.withValue(headerValue));
                return this;
            }

            /**
             * Configure an additional header.
             *
             * @param header header value
             * @return updated builder
             */
            public Builder header(Http.HeaderValue header) {
                this.headers.add(header);
                return this;
            }
        }
    }
}

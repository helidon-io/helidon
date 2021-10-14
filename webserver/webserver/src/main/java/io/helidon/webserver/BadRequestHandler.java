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

package io.helidon.webserver;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

/**
 * An error handler that is invoked when a bad request is identified.
 */
@FunctionalInterface
public interface BadRequestHandler {
    /**
     * Bad request handler, <b>MUST NOT block the current thread.</b>
     * <p>
     * This method should be used to return custom status, header and possible entity (retrieved without blocking).
     * If there is a need to handle more details, please redirect the client to a proper endpoint to handle them.
     *
     * @param request request as received with as much known information as possible
     * @param t throwable caught as part of processing with possible additional details about the reason of failure
     * @return response to use to return to original request
     */
    TransportResponse handle(TransportRequest request, Throwable t);

    /**
     * Request information.
     * Note that the information may not be according to specification, as this marks a bad request (by definition).
     */
    interface TransportRequest {
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
        String uri();

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
    class TransportResponse {
        private final Http.ResponseStatus status;
        private final Map<String, List<String>> headers;
        private final byte[] entity;

        private TransportResponse(Builder builder) {
            this.status = builder.status;
            this.headers = builder.headers;
            this.entity = builder.entity;
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
         * Create a response with {@link Http.Status#BAD_REQUEST_400} status and provided message.
         *
         * @param message message to send as response entity
         * @return a new response
         */
        public static TransportResponse create(String message) {
            return builder().entity(message).build();
        }

        Http.ResponseStatus status() {
            return status;
        }

        Map<String, List<String>> headers() {
            return headers;
        }

        Optional<byte[]> entity() {
            return Optional.ofNullable(entity);
        }

        /**
         * Fluent API builder for {@link io.helidon.webserver.BadRequestHandler.TransportResponse}.
         */
        public static class Builder implements io.helidon.common.Builder<TransportResponse> {
            private final Map<String, List<String>> headers = new HashMap<>();

            private Http.ResponseStatus status = Http.Status.BAD_REQUEST_400;
            private byte[] entity;

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
            public Builder status(Http.ResponseStatus status) {
                this.status = status;
                return this;
            }

            /**
             * Add/replace a header.
             *
             * @param name name of the header
             * @param values value of the header
             * @return updated builder
             * @throws java.lang.IllegalArgumentException if an attempt is made to modify protected headers (such as Connection)
             */
            public Builder header(String name, String... values) {
                if (name.equalsIgnoreCase(Http.Header.CONNECTION)) {
                    throw new IllegalArgumentException(
                            "Connection header cannot be overridden, it is always set to Close fro transport errors");
                }
                this.headers.put(name, List.of(values));
                return this;
            }

            /**
             * Custom entity. Uses the content, encodes it for HTML, reads it as {@code UTF-8}, configures
             * {@code Content-Length} header, configures {@code Content-Type} header to {@code text/plain}.
             * <p>
             * Use {@link #entity(byte[])} for custom encoding.
             *
             * @param entity response entity
             * @return updated builder
             */
            public Builder entity(String entity) {
                this.headers.putIfAbsent(Http.Header.CONTENT_TYPE, List.of(MediaType.TEXT_PLAIN.toString()));
                return entity(HtmlEncoder.encode(entity).getBytes(StandardCharsets.UTF_8));
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
                    this.headers.remove(Http.Header.CONTENT_LENGTH);
                } else {
                    this.header(Http.Header.CONTENT_LENGTH, String.valueOf(entity.length));
                }
                return this;
            }
        }
    }
}

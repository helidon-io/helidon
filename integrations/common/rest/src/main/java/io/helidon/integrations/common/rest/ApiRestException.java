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

package io.helidon.integrations.common.rest;

import java.util.Formatter;
import java.util.Optional;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;

/**
 * Exception when invoking remote REST API caused by wrong response from the API call.
 * This exception means the API was invoked, we got a response back, but the status of the
 * response was not valid for the invoked request.
 * Each implementation is expected to extend this class and its builder to provide
 * better details for the integrated system (such as system specific error messages).
 */
public abstract class ApiRestException extends ApiException {
    private final String requestId;
    private final Http.Status status;
    private final Headers headers;
    private final String apiSpecificError;

    /**
     * Create a new instance using base builder.
     *
     * @param builder builder
     */
    protected ApiRestException(BaseBuilder<?> builder) {
        super(builder.requestId + ": " + builder.message, builder.cause);

        this.requestId = builder.requestId;
        this.status = builder.status;
        this.headers = builder.headers;
        this.apiSpecificError = builder.apiSpecificError;
    }

    /**
     * Returned HTTP status.
     *
     * @return status
     */
    public Http.Status status() {
        return status;
    }

    /**
     * Request ID used to invoke the request.
     * This may have been generated by {@link io.helidon.integrations.common.rest.RestApi}.
     *
     * @return request ID
     */
    public String requestId() {
        return requestId;
    }

    /**
     * API specific error message if such is available.
     *
     * @return api specific error, probably obtained from a header or entity
     */
    public Optional<String> apiSpecificError() {
        return Optional.ofNullable(apiSpecificError);
    }

    /**
     * Response HTTP headers.
     *
     * @return headers
     */
    public Headers headers() {
        return headers;
    }

    /**
     * Base builder extended by specific builder class.
     *
     * @param <B> type of the subclass
     */
    public abstract static class BaseBuilder<B extends BaseBuilder<B>> {
        private String message;
        private String requestId;
        private Http.Status status;
        private Headers headers;
        private String apiSpecificError;
        private Throwable cause;

        /**
         * Message configured by {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param message message with explanation of exception
         * @return updated builder
         */
        public B message(String message) {
            this.message = message;
            return me();
        }

        /**
         * Message configured by {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param format a {@link Formatter} string
         * @param args   format string arguments
         * @return updated builder
         */
        public B message(String format, Object... args) {
            this.message = String.format(format, args);
            return me();
        }

        /**
         * Request ID configured by {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param requestId request ID
         * @return updated builder
         */
        public B requestId(String requestId) {
            this.requestId = requestId;
            return me();
        }

        /**
         * HTTP status configured by {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param status returned status
         * @return updated builder
         */
        public B status(Http.Status status) {
            this.status = status;
            return me();
        }

        /**
         * HTTP headers configured by {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param headers returned headers
         * @return updated builder
         */
        public B headers(Headers headers) {
            this.headers = headers;
            return me();
        }

        /**
         * Error specific to the integrated system, configured by implementation of the
         * {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param apiSpecificError specific exception
         * @return updated builder
         */
        public B apiSpecificError(String apiSpecificError) {
            this.apiSpecificError = apiSpecificError;
            return me();
        }

        /**
         * Possible cause of this exception (such as when we fail to parse returned entity).
         *
         * @param t cause of the new throwable
         * @return updated builder
         */
        public B cause(Throwable t) {
            this.cause = t;
            return me();
        }

        /**
         * Used to return correct type for setter methods of this builder.
         *
         * @return instance of this class typed as the subclass
         */
        @SuppressWarnings("unchecked")
        protected B me() {
            return (B) this;
        }
    }
}

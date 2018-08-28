/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.Builder;
import io.helidon.common.CollectionsHelper;

/**
 * Response from security provider (and security Module).
 */
public abstract class SecurityResponse {
    private final Map<String, List<String>> requestHeaders;
    private final Map<String, List<String>> responseHeaders;
    private final SecurityStatus status;
    private final String description;
    private final Throwable throwable;
    private final int statusCode;

    SecurityResponse(SecurityResponseBuilder<? extends SecurityResponseBuilder<?, ?>, ?> builder) {
        this.status = builder.status;
        this.description = builder.description;
        this.throwable = builder.throwable;
        this.requestHeaders = builder.requestHeaders;
        this.responseHeaders = builder.responseHeaders;
        this.statusCode = builder.statusCode;
    }

    /**
     * Synchronize a completion stage.
     *
     * @param stage future response
     * @param <T>   type the future response will provide
     * @return instance the future returns
     * @throws SecurityException in case of timeout, interrupted call or exception during future processing
     */
    static <T> T get(CompletionStage<T> stage) {
        try {
            // since java 9 this method is not optional, so we can safely call it
            return stage.toCompletableFuture().get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new SecurityException("Interrupted while waiting for completion stage to complete", e);
        } catch (ExecutionException e) {
            throw new SecurityException("Failure while executing asynchronous security", e);
        } catch (TimeoutException e) {
            throw new SecurityException("Timed out after waiting for completion stage to complete", e);
        }
    }

    /**
     * Status of this response.
     *
     * @return SecurityStatus as the provider responded
     */
    public SecurityStatus getStatus() {
        return status;
    }

    /**
     * Status code (uses HTTP status codes for mapping).
     *
     * @return HTTP status code the provider wants to use, or empty if not set
     */
    public OptionalInt getStatusCode() {
        return (statusCode == -1 ? OptionalInt.empty() : OptionalInt.of(statusCode));
    }

    /**
     * Description of current security status. Should be provided by security providers mostly for failure cases.
     *
     * @return Description of current status (optional)
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * Get underlying throwable causing a failure state (if such happened).
     *
     * @return Exception causing current failure (optional)
     */
    public Optional<Throwable> getThrowable() {
        return Optional.ofNullable(throwable);
    }

    /**
     * Get new request headers to be used. These may be additional header, replacement headers or "clearing" headers (in case
     * the value is empty list).
     *
     * @return Map of headers to merge with existing headers
     */
    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * Get new response headers to be used. These may be additional header, replacement headers or "clearing" headers (in case
     * the value is empty list).
     *
     * @return Map of headers to merge with existing headers
     */
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public String toString() {
        return "SecurityResponse{"
                + "status=" + status
                + ", description='" + description + '\''
                + ", statusCode=" + statusCode
                + '}';
    }

    /**
     * Status of a security operation.
     */
    public enum SecurityStatus {
        /**
         * Indicates that the message processing by the security component
         * was successful and that the runtime is to proceed with its normal
         * processing of the resulting message.
         * Example: authentication successful, continue with request processing.
         */
        SUCCESS(true),
        /**
         * Succeeded and provider did everything to be done.
         * Finish processing (do nothing more in current flow).
         *
         * The provider should have:
         * <ul>
         * <li>Updated entity (through {@link SecurityRequest#getResponseEntity()}</li>
         * <li>Updated headers (through {@link SecurityResponseBuilder#responseHeader(String, String)}</li>
         * <li>Updated status code (through {@link SecurityResponseBuilder#statusCode(int)}</li>
         * </ul>
         */
        SUCCESS_FINISH(true),
        /**
         * Indicates that the message processing by the security module
         * was NOT successful.
         * Example: authorization failure
         */
        FAILURE,
        /**
         * Failed and provider did everything to be done. Finish processing (do nothing more in current flow).
         */
        FAILURE_FINISH,
        /**
         * Cannot process, not an error.
         */
        ABSTAIN;

        private final boolean success;

        SecurityStatus() {
            this(false);
        }

        SecurityStatus(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Builder for security response.
     *
     * @param <T> Type of security response to build
     */
    abstract static class SecurityResponseBuilder<T extends SecurityResponseBuilder<T, B>, B> implements Builder<B> {
        private final Map<String, List<String>> requestHeaders = new HashMap<>();
        private final Map<String, List<String>> responseHeaders = new HashMap<>();
        private final T myInstance;
        private SecurityStatus status = SecurityStatus.SUCCESS;
        private String description;
        private Throwable throwable;
        private int statusCode = -1;

        @SuppressWarnings("unchecked")
        SecurityResponseBuilder() {
            this.myInstance = (T) this;
        }

        /**
         * Set a status code for failed statuses. This is expected to use HTTP status
         * codes. If an integration is done with a non-http protocol, you must map these
         * status codes appropriately.
         *
         * @param statusCode HTTP status code
         * @return updated builder instance
         */
        public T statusCode(int statusCode) {
            this.statusCode = statusCode;
            return myInstance;
        }

        /**
         * Set security status of this security response.
         *
         * @param status Status to set
         * @return updated builder instance
         */
        public T status(SecurityStatus status) {
            this.status = status;
            return myInstance;
        }

        /**
         * Set description of this security response failure.
         *
         * @param description Description to provide to called in case of failed security, or null if no information can be
         *                    provided
         * @return updated builder instance
         */
        public T description(String description) {
            this.description = description;
            return myInstance;
        }

        /**
         * Set throwable causing failure of the security request.
         *
         * @param exception Exception that caused failure
         * @return updated builder instance
         */
        public T throwable(Throwable exception) {
            this.throwable = exception;
            return myInstance;
        }

        /**
         * Set additional/replacement headers for request.
         *
         * @param headers map with headers
         * @return updated builder instance
         */
        public T requestHeaders(Map<String, List<String>> headers) {
            this.requestHeaders.clear();
            this.requestHeaders.putAll(headers);
            return myInstance;
        }

        /**
         * Add a single-value header. Note that if method {@link #requestHeaders(Map)} is called after
         * this method, it will remove changes by this method.
         *
         * @param header header name
         * @param value  header value
         * @return this instance
         */
        public T requestHeader(String header, String value) {
            requestHeaders.put(header, CollectionsHelper.listOf(value));
            return myInstance;
        }

        /**
         * Add a multi-value header. Note that if method {@link #requestHeaders(Map)} is called after
         * this method, it may remove changes by this method.
         *
         * @param header header name
         * @param values header values
         * @return this instance
         */
        public T requestHeader(String header, List<String> values) {
            requestHeaders.put(header, values);
            return myInstance;
        }

        /**
         * Set additional/replacement headers for request.
         *
         * @param headers map with headers
         * @return updated builder instance
         */
        public T responseHeaders(Map<String, List<String>> headers) {
            this.responseHeaders.clear();
            this.responseHeaders.putAll(headers);
            return myInstance;
        }

        /**
         * Add a single-value header. Note that if method {@link #responseHeaders(Map)} is called after
         * this method, it will remove changes by this method.
         *
         * @param header header name
         * @param value  header value
         * @return this instance
         */
        public T responseHeader(String header, String value) {
            responseHeaders.put(header, CollectionsHelper.listOf(value));
            return myInstance;
        }

        /**
         * Add a multi-value header. Note that if method {@link #responseHeaders(Map)} is called after
         * this method, it may remove changes by this method.
         *
         * @param header header name
         * @param values header values
         * @return this instance
         */
        public T responseHeader(String header, List<String> values) {
            responseHeaders.put(header, values);
            return myInstance;
        }
    }
}

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

import java.util.Optional;

/**
 * Response as returned from an authentication provider. Provider should return a response even for failed authentications.
 * Only throw an exception if an error has occurred (e.g. misconfiguration). Do not throw exception
 * for bad credentials...
 *
 * @see #failed(String)
 * @see #success(Subject)
 */
public final class AuthenticationResponse extends SecurityResponse {
    private final Optional<Subject> user;
    private final Optional<Subject> service;

    AuthenticationResponse(Builder builder) {
        super(builder);
        this.user = Optional.ofNullable(builder.user);
        this.service = Optional.ofNullable(builder.service);
    }

    /**
     * Get a builder for more complex responses.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Construct a failed response with a throwable as a cause.
     *
     * @param message Descriptive message of what happened. This message is propagated to public API!
     * @param cause   Throwable causing the failed authentication. This will be logged. It may reach user only in case of debug.
     * @return AuthenticationResponse with information filled
     */
    public static AuthenticationResponse failed(String message, Throwable cause) {
        return builder().description(message).throwable(cause).status(SecurityStatus.FAILURE).build();
    }

    /**
     * Construct a failed response with an explanatory message.
     *
     * @param message Descriptive message of what happened. This message is propagated to public API!
     * @return AuthenticationResponse with information filled
     */
    public static AuthenticationResponse failed(String message) {
        return builder().description(message).status(SecurityStatus.FAILURE).build();
    }

    /**
     * Provider returning this response is not capable to make a decision (e.g. the user format is not supported).
     *
     * @return AuthenticationResponse with information filled
     */
    public static AuthenticationResponse abstain() {
        return builder().status(SecurityStatus.ABSTAIN).build();
    }

    /**
     * Provider has authenticated the request and created a user Subject.
     *
     * @param subject Subject of the current user
     * @return AuthenticationResponse with information filled
     */
    public static AuthenticationResponse success(Subject subject) {
        return builder().status(SecurityStatus.SUCCESS).user(subject).build();
    }

    /**
     * Provider has authenticated the request and created a user and service Subject.
     *
     * @param user    Subject of the current user
     * @param service Subject of the current service
     * @return AuthenticationResponse with information filled
     */
    public static AuthenticationResponse success(Subject user, Subject service) {
        return builder().status(SecurityStatus.SUCCESS)
                .user(user)
                .service(service)
                .build();
    }

    /**
     * Provider has authenticated the request and created a service Subject.
     *
     * @param service Subject of requesting service (or client)
     * @return AuthenticationResponse with information filled
     */
    public static AuthenticationResponse successService(Subject service) {
        return builder().status(SecurityStatus.SUCCESS).service(service).build();
    }

    /**
     * Provider has authenticated the request and created a principal for a user.
     *
     * @param principal principal of the user
     * @return AuthenticationResponse with information filled
     * @see #success(Subject)
     * @see #successService(Subject)
     * @see #successService(Principal)
     */
    public static AuthenticationResponse success(Principal principal) {
        return success(Subject.builder()
                               .principal(principal)
                               .build());
    }

    /**
     * Provider has authenticated the request and created a principal for a service (or a client).
     *
     * @param principal principal of the service
     * @return AuthenticationResponse with information filled
     * @see #successService(Subject)
     * @see #success(Subject)
     * @see #success(Principal)
     */
    public static AuthenticationResponse successService(Principal principal) {
        return successService(Subject.builder()
                                      .principal(principal)
                                      .build());
    }

    public Optional<Subject> getUser() {
        return user;
    }

    public Optional<Subject> getService() {
        return service;
    }

    @Override
    public String toString() {
        return "AuthenticationResponse{"
                + super.toString()
                + "user=" + user
                + ", service=" + service
                + '}';
    }

    /**
     * Authentication response builder.
     * Allows fully customized response, if one of the static methods is not sufficient (e.g. when returning a specialized
     * {@link SecurityStatus}.
     */
    public static class Builder extends SecurityResponse.SecurityResponseBuilder<Builder, AuthenticationResponse> {
        private Subject user;
        private Subject service;

        /**
         * Set the user subject as created by this provider.
         *
         * @param subject Subject to set for current request
         * @return updated builder instance
         */
        public Builder user(Subject subject) {
            this.user = subject;
            return this;
        }

        /**
         * Set the service subject as created by this provider.
         *
         * @param subject Subject to set for current request
         * @return updated builder instance
         */
        public Builder service(Subject subject) {
            this.service = subject;
            return this;
        }

        /**
         * Build authentication response.
         *
         * @return Response based on this builder
         */
        @Override
        public AuthenticationResponse build() {
            return new AuthenticationResponse(this);
        }
    }
}

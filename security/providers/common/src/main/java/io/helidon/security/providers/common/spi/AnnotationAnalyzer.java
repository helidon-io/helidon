/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.security.providers.common.spi;

import java.lang.reflect.Method;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.security.ClassToInstanceStore;

/**
 * Provides capability to extensions to enforce authentication and authorization even when
 * the resource is not annotated with io.helidon.security.annotations.Authenticated or
 * io.helidon.security.annotations.Authorized.
 * This is loaded using a {@link java.util.ServiceLoader} - integration with Security is done automatically as long as the
 * implementation is discovered as a java service.
 */
@FunctionalInterface
public interface AnnotationAnalyzer {
    /**
     * Provides configuration on node "security.jersey.analyzers".
     *
     * @param config config to use to configure an analyzer, may be empty (e.g. have reasonable defaults if possible)
     */
    default void init(Config config) {
    }

    /**
     * Analyze an application class.
     *
     * @param maybeAnnotated class of the JAX-RS application
     * @return response with information whether to (and how) authenticate and authorize
     */
    AnalyzerResponse analyze(Class<?> maybeAnnotated);

    /**
     * Analyze a resource class.
     * By default returns an abstain response.
     *
     * @param maybeAnnotated   class of the JAX-RS resource
     * @param previousResponse response from parent of this class (e.g. from application analysis)
     * @return response with information whether to (and how) authenticate and authorize
     */
    default AnalyzerResponse analyze(Class<?> maybeAnnotated, AnalyzerResponse previousResponse) {
        return AnalyzerResponse.abstain(previousResponse);
    }

    /**
     * Analyze a resource method.
     * By default returns an abstain response.
     *
     * @param maybeAnnotated   JAX-RS resource method
     * @param previousResponse response from parent of this class (e.g. from resource class analysis)
     * @return response with information whether to (and how) authenticate and authorize
     */
    default AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
        return AnalyzerResponse.abstain(previousResponse);
    }

    /**
     * Flag for security type.
     */
    enum Flag {
        /**
         * Security MUST be enforced.
         */
        // must atz/atn
        REQUIRED,
        /**
         * Security MAY be used (e.g. for authentication - we may authenticate, though we may access as not-authenticated user).
         */
        // I do not care
        OPTIONAL,
        /**
         * Security MUST NOT be used (strictly public endpoint - do not invoke security).
         */
        // must not atz/atn
        FORBIDDEN,
        /**
         * This analyzer is not capable of asserting the need to do security - carry on as if it did not exist.
         */
        // I do not know
        ABSTAIN
    }

    /**
     * Response of an analysis run.
     */
    final class AnalyzerResponse {
        private final ClassToInstanceStore<Object> registry = ClassToInstanceStore.create();
        private final AnalyzerResponse parent;

        private final Flag atnResponse;
        private final Flag atzResponse;
        private final String authenticator;
        private final String authorizer;

        private AnalyzerResponse(Builder builder) {
            this.registry.putAll(builder.registry);
            this.parent = builder.parent;
            this.atnResponse = builder.atnResponse;
            this.atzResponse = builder.atzResponse;
            this.authenticator = builder.authenticator;
            this.authorizer = builder.authorizer;
        }

        /**
         * Create an abstain response (e.g. the analyzer has no information to provide a valid response, such as
         * the annotation this analyzer supports is missing).
         *
         * @return an abstain response
         */
        public static AnalyzerResponse abstain() {
            return builder()
                    .build();
        }

        /**
         * Create an abstain response (e.g. the analyzer has no information to provide a valid response, such as
         * the annotation this analyzer supports is missing).
         *
         * @param previousResponse response from previous analysis (to allow parent/child structure, e.g. when some information
         *                         is needed by further analysis)
         * @return an abstain response
         */
        public static AnalyzerResponse abstain(AnalyzerResponse previousResponse) {
            return builder(previousResponse)
                    .build();
        }

        /**
         * Create a fluent API builder.
         *
         * @return a builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a fluent API builder with a parent response.
         *
         * @param parent response from previous run
         * @return a builder
         */
        public static Builder builder(AnalyzerResponse parent) {
            return new Builder()
                    .parent(parent);
        }

        /**
         * Parent of this analysis.
         *
         * For application class analysis, the parent is empty.
         * For resource class analysis, the parent is application class analysis result.
         * For resource method analysis, the parent is resource class analysis result.
         *
         * @return parent if exists, or empty
         */
        public Optional<AnalyzerResponse> parent() {
            return Optional.ofNullable(parent);
        }

        /**
         * Authentication response of this analysis.
         *
         * @return authentication response
         */
        public Flag authenticationResponse() {
            return atnResponse;
        }

        /**
         * Authorization response of this analysis.
         *
         * @return authorization response
         */
        public Flag authorizationResponse() {
            return atzResponse;
        }

        /**
         * Explicit authentication provider name. If none provided, the default authenticator would be used.
         *
         * @return authenticator name or empty
         */
        public Optional<String> authenticator() {
            return Optional.ofNullable(authenticator);
        }

        /**
         * Explicit authorization provider name. If none provided, the default authorizer would be used.
         *
         * @return authorization name or empty
         */
        public Optional<String> authorizer() {
            return Optional.ofNullable(authorizer);
        }

        /**
         * A registry that allows transferring information between analysis of different scopes (application, resource class,
         * method).
         *
         * @return registry to use
         */
        public ClassToInstanceStore<Object> registry() {
            return registry;
        }

        /**
         * Fluent API builder for {@link AnalyzerResponse}.
         */
        public static class Builder implements io.helidon.common.Builder<AnalyzerResponse> {
            private final ClassToInstanceStore<Object> registry = ClassToInstanceStore.create();
            private AnalyzerResponse parent;
            private Flag atnResponse = Flag.ABSTAIN;
            private Flag atzResponse = Flag.ABSTAIN;
            private String authenticator;
            private String authorizer;

            @Override
            public AnalyzerResponse build() {
                return new AnalyzerResponse(this);
            }

            Builder parent(AnalyzerResponse parent) {
                this.parent = parent;
                return this;
            }

            /**
             * Register an object later available through
             * {@link io.helidon.security.providers.common.spi.AnnotationAnalyzer.AnalyzerResponse#registry()}.
             *
             * @param anInstance instance to register by its class
             * @return updated builder instance
             */
            public Builder register(Object anInstance) {
                registry.putInstance(anInstance);
                return this;
            }

            /**
             * Register an object later available through
             * {@link io.helidon.security.providers.common.spi.AnnotationAnalyzer.AnalyzerResponse#registry()}.
             *
             * @param theClass class to register the instance by
             * @param anInstance instance to register
             * @param <T> type of instance
             * @return updated builder instance
             */
            public <T> Builder register(Class<? super T> theClass, T anInstance) {
                registry.putInstance(theClass, anInstance);
                return this;
            }

            /**
             * Authentication response.
             *
             * @param atnResponse response for authentication
             * @return updated builder instance
             */
            public Builder authenticationResponse(Flag atnResponse) {
                this.atnResponse = atnResponse;
                return this;
            }

            /**
             * Authorization response.
             *
             * @param authorizeResponse authorization response flag
             * @return updated builder instance
             */
            public Builder authorizeResponse(Flag authorizeResponse) {
                this.atzResponse = authorizeResponse;
                return this;
            }

            /**
             * Explicit authentication provider to use.
             *
             * @param authenticator name of a provider to use
             * @return updated builder instance
             */
            public Builder authenticator(String authenticator) {
                this.authenticator = authenticator;
                return this;
            }

            /**
             * Explicit authorization provider to use.
             *
             * @param authorizer name of a provider to use
             * @return updated builder instance
             */
            public Builder authorizer(String authorizer) {
                this.authorizer = authorizer;
                return this;
            }
        }
    }
}



/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * Security context to retrieve security information about current user, either injected or obtained from {@link
 * Security#contextBuilder(String)} and to handle programmatic security.
 */
public interface SecurityContext {
    /**
     * Anonymous user principal.
     * This is the user principal used when no user is authenticated (e.g. when a service is authenticated or when
     * fully {@link #ANONYMOUS}.
     */
    Principal ANONYMOUS_PRINCIPAL = Principal.builder()
            .name("<ANONYMOUS>")
            .addAttribute("anonymous", true)
            .build();

    /**
     * Anonymous subject.
     * This is the subject you get when not authenticated and a Subject is required..
     */
    Subject ANONYMOUS = Subject.builder()
            .principal(ANONYMOUS_PRINCIPAL)
            .addAttribute("anonymous", true)
            .build();

    /**
     * A builder to build a {@link SecurityRequest}.
     *
     * @return security request builder
     */
    SecurityRequestBuilder<?> securityRequestBuilder();

    /**
     * A builder to build a {@link SecurityRequest} with a specific environment.
     *
     * @param environment environment to use for this request
     * @return security request builder
     */
    SecurityRequestBuilder<?> securityRequestBuilder(SecurityEnvironment environment);

    /**
     * Authenticator client builder to use for programmatic authentication.
     *
     * @return a builder for {@link SecurityClient} instance providing {@link AuthenticationResponse}
     */
    SecurityClientBuilder<AuthenticationResponse> atnClientBuilder();

    /**
     * Authenticate current request (based on current {@link SecurityEnvironment} and {@link EndpointConfig}.
     *
     * @return response of authentication operation
     */
    AuthenticationResponse authenticate();

    /**
     * Authorization client builder to use for programmatic authorization.
     * Will use existing environment.
     *
     * @return a builder for {@link SecurityClient} instance providing {@link AuthorizationResponse}
     */
    SecurityClientBuilder<AuthorizationResponse> atzClientBuilder();

    /**
     * Outbound security client builder for programmatic outbound security used for identity propagation, identity mapping,
     * encryption of outbound calls etc.
     *
     * @return a builder for {@link SecurityClient} instance providing {@link OutboundSecurityResponse}
     */
    OutboundSecurityClientBuilder outboundClientBuilder();

    /**
     * Authorize access to a resource (or more resources) based on current environment and endpoint configuration.
     *
     * @param resource resources to authorize access to (may be empty)
     * @return response of authorization
     */
    AuthorizationResponse authorize(Object... resource);

    /**
     * Return true if the user is authenticated.
     * This only cares about USER! not about service. To check if service is authenticated, use
     * {@link #service()} and check the resulting optional.
     *
     * @return true for authenticated user, false otherwise (e.g. no subject or {@link #ANONYMOUS})
     */
    //JSR 375
    boolean isAuthenticated();

    /**
     * Logout user, clear current security context.
     */
    //JSR 375
    void logout();

    /**
     * Check if user is in specified role if supported by global or specific authorization provider.
     *
     * @param role           Role to check
     * @param authorizerName explicit authorization provider class name to use (or config property pointing to class name)
     * @return true if current user is in specified role and current authorization provider supports roles, false otherwise
     */
    boolean isUserInRole(String role,
                         String authorizerName);

    /**
     * Executor service of the security module.
     *
     * @return executor service to use to execute asynchronous tasks related to security
     */
    ExecutorService executorService();

    /**
     * Check if user is in specified role if supported by global authorization provider.
     *
     * This method expects global authorization provider is in use. If you explicitly use a custom provider, use {@link
     * #isUserInRole(String, String)} instead.
     *
     * @param role Role to check
     * @return true if current user is in specified role and current authorization provider supports roles, false otherwise
     */
    boolean isUserInRole(String role);

    /**
     * Audit a security event. This allows custom auditing events from applications.
     * Note that main security events are already audited
     * (e.g. authentication, authorization, identity propagation and various runAs events).
     *
     * @param event AuditEvent to store
     */
    void audit(AuditEvent event);

    /**
     * Returns subject of current context (caller) service or client identity.
     *
     * @return current context service (client) subject. If there is no service/client, returns empty.
     */
    Optional<Subject> service();

    /**
     * Returns service principal if service is authenticated.
     *
     * @return current context service principal, or empty if none authenticated
     */
    default Optional<Principal> servicePrincipal() {
        return service().map(Subject::principal);
    }

    /**
     * A helper method to get service name if authenticated.
     *
     * @return name of currently authenticated service or null.
     */
    default String serviceName() {
        return servicePrincipal().map(Principal::getName).orElse(null);
    }

    /**
     * Returns subject of current context (caller) user.
     *
     * @return current context user subject. If there is no authenticated user, returns empty.
     */
    Optional<Subject> user();

    /**
     * Returns user principal if user is authenticated.
     *
     * @return current context user principal, or empty if none authenticated
     */
    default Optional<Principal> userPrincipal() {
        return user().map(Subject::principal);
    }

    /**
     * A helper method to get user name if authenticated.
     *
     * @return name of currently authenticated user or null.
     */
    default String userName() {
        return userPrincipal().map(Principal::getName).orElse(null);
    }

    /**
     * Executes provided code under provided subject.
     *
     * @param subject  to use for execution. Use {@link #ANONYMOUS} for anon.
     * @param runnable to execute.
     */
    void runAs(Subject subject, Runnable runnable);

    /**
     * Execute provided code as current user with an additional explicit role added.
     *
     * @param role     name of role
     * @param runnable to execute
     */
    void runAs(String role, Runnable runnable);

    /**
     * Provides the span for tracing. This is the span of current context (e.g. parent to security).
     *
     * @return Open tracing Span context of current security context
     */
    SpanContext tracingSpan();

    /**
     * Provides the tracer to create new spans. If you use this, we can control whether tracing is enabled or disabled
     * as part of security.
     * If you use {@link io.opentracing.util.GlobalTracer#get()} you will get around this.
     *
     * @return {@link Tracer} to build custom {@link Span Spans}. Use in combination with {@link #tracingSpan()} to
     * create a nice tree of spans
     */
    Tracer tracer();

    /**
     * Id of this context instance. Created as security instance id : context id (depends on container integration or
     * id provided by developer).
     *
     * @return id uniquely identifying this context
     */
    String id();

    /**
     * Get time instance, that can be used to obtain current time consistent with the security framework.
     * This time may be shifted against real time, may have a different time zone, explicit values (for testing).
     * To obtain the decisive time for current request, please use {@link SecurityEnvironment}.
     *
     * @return time instance to obtain current time
     * @see SecurityTime#get()
     */
    SecurityTime serverTime();

    /**
     * Current {@link SecurityEnvironment}. For web, this probably won't change, as the environment
     * is valid for whole request. For other frameworks or standalone applications, this may change
     * over time.
     *
     * @return environment of current security context (e.g. to use for ABAC)
     */
    SecurityEnvironment env();

    /**
     * Set a new security environment to be used int this context.
     *
     * @param envBuilder builder to build environment from
     * @see SecurityEnvironment#derive()
     * @see SecurityEnvironment#builder(SecurityTime)
     */
    default void env(Supplier<SecurityEnvironment> envBuilder) {
        env(envBuilder.get());
    }

    /**
     * Set a new security environment to be used in this context.
     *
     * @param env environment to use for further security operations
     * @see SecurityEnvironment#derive()
     */
    void env(SecurityEnvironment env);

    /**
     * Current endpoint configuration.
     *
     * @return configuration specific to current endpoint (annotations, config, custom object, attributes)
     */
    EndpointConfig endpointConfig();

    /**
     * Set endpoint configuration to use for subsequent security requests.
     *
     * @param ec configuration specific to current endpoint (annotations, config, custom object, attributes)
     */
    void endpointConfig(EndpointConfig ec);

    /**
     * Shortcut method to set {@link EndpointConfig} using a builder rather than built instance.
     * Shortcut to {@link #endpointConfig(EndpointConfig)}
     *
     * @param epBuilder builder of an endpoint configuration
     */
    default void endpointConfig(Supplier<EndpointConfig> epBuilder) {
        endpointConfig(epBuilder.get());
    }

    /**
     * Return true if either of authorization methods ({@link #authorize(Object...)} or {@link #atzClientBuilder()}
     * was called).
     * This is a safe-guard for attribute based authorization that is using annotations and requires object to be passed
     * for evaluation.
     *
     * @return true if authorization was checked, false otherwise
     */
    boolean atzChecked();

    /**
     * Fluent API builder for {@link SecurityContext}.
     */
    class Builder implements io.helidon.common.Builder<SecurityContext> {
        private final Security security;
        private String id;
        private Supplier<ExecutorService> executorServiceSupplier;
        private SecurityTime serverTime;
        private Tracer tracingTracer;
        private SpanContext tracingSpan;
        private SecurityEnvironment env;
        private EndpointConfig ec;

        Builder(Security security) {
            this.security = security;
            this.executorServiceSupplier = security.executorService();
        }

        @Override
        public SecurityContext build() {
            if (null == env) {
                env = SecurityEnvironment.builder(serverTime).build();
            }
            if (null == ec) {
                ec = EndpointConfig.builder().build();
            }
            if (null == tracingTracer) {
                tracingTracer = GlobalTracer.get();
            }
            return new SecurityContextImpl(this);
        }

        Security security() {
            return security;
        }

        String id() {
            return id;
        }

        Supplier<ExecutorService> executorServiceSupplier() {
            return executorServiceSupplier;
        }

        SecurityTime serverTime() {
            return serverTime;
        }

        Tracer tracingTracer() {
            return tracingTracer;
        }

        SpanContext tracingSpan() {
            return tracingSpan;
        }

        SecurityEnvironment env() {
            return env;
        }

        EndpointConfig endpointConfig() {
            return ec;
        }

        /**
         * Id of the new security context. This should be usable for correlation of log records,
         * traces etc.
         * <p>
         * Use this method only if you need to override default behavior!
         *
         * @param id id to use, by default this used security UUID post-fixed by id you gave
         *           to {@link Security#contextBuilder(String)}
         * @return updated builder instance
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Executor service to use for requests within this context.
         * By default uses a custom executor service that is configured when building
         * {@link Security} instance.
         * <p>
         * Use this method only if you need to override default behavior!
         *
         * @param executorServiceSupplier supplier of an executor service
         * @return updated builder instance
         */
        public Builder executorService(Supplier<ExecutorService> executorServiceSupplier) {
            this.executorServiceSupplier = executorServiceSupplier;
            return this;
        }

        /**
         * Executor service to use for requests within this context.
         * By default uses a custom executor service that is configured when building
         * {@link Security} instance.
         * <p>
         * Use this method only if you need to override default behavior!
         *
         * @param executorService executor service
         * @return updated builder instance
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorServiceSupplier = () -> executorService;
            return this;
        }

        /**
         * SecurityTime to use when determining current time. Used e.g. when
         * creating a new {@link SecurityEnvironment}.
         * By default uses server time that is configured for {@link Security} instance
         * <p>
         * Use this method only if you need to override default behavior!
         *
         * @param serverTime the server time to use
         * @return updated builder instance
         */
        public Builder serverTime(SecurityTime serverTime) {
            this.serverTime = serverTime;
            return this;
        }

        /**
         * Tracer used to create new span contexts when tracing security events.
         * By default uses tracer of {@link Security} instance.
         * <p>
         * Use this method only if you need to override default behavior!
         *
         * @param tracingTracer tracer to use
         * @return updated builder instance
         */
        public Builder tracingTracer(Tracer tracingTracer) {
            this.tracingTracer = tracingTracer;
            return this;
        }

        /**
         * Open tracing span context to correctly trace security.
         *
         * @param tracingSpan Open tracing span context of the request within which we create this security context
         * @return updated builder instance
         */
        public Builder tracingSpan(SpanContext tracingSpan) {
            this.tracingSpan = tracingSpan;
            return this;
        }

        /**
         * Set the security environment to start with.
         *
         * @param env environment to use for security requests
         * @return updated builder instance
         */
        public Builder env(SecurityEnvironment env) {
            this.env = env;
            return this;
        }

        /**
         * Set the endpoint configuration to start with.
         *
         * @param ec configuration specific to an endpoint (including annotations, custom objects etc.)
         * @return updated builder instance
         */
        public Builder endpointConfig(EndpointConfig ec) {
            this.ec = ec;
            return this;
        }
    }
}

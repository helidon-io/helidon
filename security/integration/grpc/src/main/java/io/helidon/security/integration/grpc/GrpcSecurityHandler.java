/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.integration.grpc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.security.AuditEvent;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ClassToInstanceStore;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityRequest;
import io.helidon.security.SecurityRequestBuilder;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.AtnTracing;
import io.helidon.security.integration.common.AtzTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.internal.SecurityAuditEvent;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentracing.SpanContext;

import static io.helidon.security.AuditEvent.AuditParam.plain;

/**
 * Handles security for the gRPC server. This handler is registered either by hand on the gRPC routing config,
 * or automatically from configuration when integration is done through {@link GrpcSecurity#create(Config)}
 * or {@link GrpcSecurity#create(Security)}.
 * <p>
 * This class is an implementation of a {@link ServerInterceptor} with a priority of
 * {@link InterceptorPriorities#CONTEXT} that will add itself to the call context with the key
 * {@link GrpcSecurity#GRPC_SECURITY_HANDLER}. This will then cause the {@link GrpcSecurity}
 * interceptor that runs later with a priority of {@link InterceptorPriorities#AUTHENTICATION} to use
 * this instance of the handler.
 */
// we need to have all fields optional and this is cleaner than checking for null
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Priority(InterceptorPriorities.CONTEXT)
public class GrpcSecurityHandler
        implements ServerInterceptor, ServiceDescriptor.Configurer {
    private static final Logger LOGGER = Logger.getLogger(GrpcSecurityHandler.class.getName());
    private static final String KEY_ROLES_ALLOWED = "roles-allowed";
    private static final String KEY_AUTHENTICATOR = "authenticator";
    private static final String KEY_AUTHORIZER = "authorizer";
    private static final String KEY_AUTHENTICATE = "authenticate";
    private static final String KEY_AUTHENTICATION_OPTIONAL = "authentication-optional";
    private static final String KEY_AUTHORIZE = "authorize";
    private static final String KEY_AUDIT = "audit";
    private static final String KEY_AUDIT_EVENT_TYPE = "audit-event-type";
    private static final String KEY_AUDIT_MESSAGE_FORMAT = "audit-message-format";
    private static final String DEFAULT_AUDIT_EVENT_TYPE = "grpcRequest";
    private static final String DEFAULT_AUDIT_MESSAGE_FORMAT = "%2$s %1$s %4$s %5$s requested by %3$s";
    private static final GrpcSecurityHandler DEFAULT_INSTANCE = builder().build();

    private final Optional<Set<String>> rolesAllowed;
    private final Optional<ClassToInstanceStore<Object>> customObjects;
    private final Optional<Config> config;
    private final Optional<String> explicitAuthenticator;
    private final Optional<String> explicitAuthorizer;
    private final Optional<Boolean> authenticate;
    private final Optional<Boolean> authenticationOptional;
    private final Optional<Boolean> authorize;
    private final Optional<Boolean> audited;
    private final Optional<String> auditEventType;
    private final Optional<String> auditMessageFormat;
    private final boolean combined;
    private final Map<String, Config> configMap = new HashMap<>();

    // lazily initialized (as it requires a context value to first create it)
    private final AtomicReference<GrpcSecurityHandler> combinedHandler = new AtomicReference<>();

    private GrpcSecurityHandler(Builder builder) {
        // must copy values to be safely immutable
        this.rolesAllowed = builder.rolesAllowed.flatMap(strings -> {
            Set<String> newRoles = new HashSet<>(strings);
            return Optional.of(newRoles);
        });

        // must copy values to be safely immutable
        this.customObjects = builder.customObjects.flatMap(store -> {
            ClassToInstanceStore<Object> ctis = new ClassToInstanceStore<>();
            ctis.putAll(store);
            return Optional.of(ctis);
        });

        config = builder.config;
        explicitAuthenticator = builder.explicitAuthenticator;
        explicitAuthorizer = builder.explicitAuthorizer;
        authenticate = builder.authenticate;
        authenticationOptional = builder.authenticationOptional;
        audited = builder.audited;
        auditEventType = builder.auditEventType;
        auditMessageFormat = builder.auditMessageFormat;
        authorize = builder.authorize;
        combined = builder.combined;

        config.ifPresent(conf -> {
            if (conf.exists() && !conf.isLeaf()) {
                conf.asNodeList().get().forEach(node -> configMap.put(node.name(), node));
            }
        });
    }

    /**
     * Create an instance from configuration.
     * <p>
     * The config expected (example in HOCON format):
     * <pre>
     * {
     *   #
     *   # these are used by {@link GrpcSecurity} when loaded from config, to register
     *   # with the {@link io.helidon.grpc.server.GrpcServer}
     *   #
     *   path = "/noRoles"
     *   methods = ["get"]
     *
     *   #
     *   # these are used by this class
     *   #
     *   # whether to authenticate this request - defaults to false (even if authorize is true)
     *   authenticate = true
     *   # if set to true, authentication failure will not abort request and will continue as anonymous (defaults to false)
     *   authentication optional
     *   # use a named authenticator (as supported by security - if not defined, default authenticator is used)
     *   authenticator = "basic-auth"
     *   # an array of allowed roles for this path - must have a security provider supporting roles
     *   roles-allowed = ["user"]
     *   # whether to authorize this request - defaults to true (authorization is "on" by default)
     *   authorize = true
     *   # use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
     *   #   permitted)
     *   authorizer = "roles"
     *   # whether to audit this request - defaults to false, if enabled, request is audited with event type "request"
     *   audit = true
     *   # override for event-type, defaults to {@value #DEFAULT_AUDIT_EVENT_TYPE}
     *   audit-event-type = "unit_test"
     *   # override for audit message format, defaults to {@value #DEFAULT_AUDIT_MESSAGE_FORMAT}
     *   audit-message-format = "Unit test message format"
     *   # override for audit severity for successful requests (1xx, 2xx and 3xx status codes),
     *   #   defaults to {@link AuditEvent.AuditSeverity#SUCCESS}
     *   audit-ok-severity = "AUDIT_FAILURE"
     *   # override for audit severity for unsuccessful requests (4xx and 5xx status codes),
     *   #   defaults to {@link AuditEvent.AuditSeverity#FAILURE}
     *   audit-error-severity = "INFO"
     *
     *   #
     *   # Any other configuration - this all gets passed to a security provider, so check your provider's documentation
     *   #
     *   custom-provider {
     *      custom-key = "some value"
     *   }
     * }
     * </pre>
     *
     * @param config   Config at the point of a single handler configuration
     * @param defaults Default values to copy
     * @return an instance configured from the config (using defaults from defaults parameter for missing values)
     */
    static GrpcSecurityHandler create(Config config, GrpcSecurityHandler defaults) {
        Builder builder = builder(defaults);

        config.get(KEY_ROLES_ALLOWED).asList(String.class)
                .ifPresentOrElse(builder::rolesAllowed,
                                 () -> defaults.rolesAllowed.ifPresent(builder::rolesAllowed));
        if (config.exists()) {
            builder.config(config);
        }

        config.get(KEY_AUTHENTICATOR).asString().or(() -> defaults.explicitAuthenticator)
                .ifPresent(builder::authenticator);
        config.get(KEY_AUTHORIZER).asString().or(() -> defaults.explicitAuthorizer)
                .ifPresent(builder::authorizer);
        config.get(KEY_AUTHENTICATE).as(Boolean.class).or(() -> defaults.authenticate)
                .ifPresent(builder::authenticate);
        config.get(KEY_AUTHENTICATION_OPTIONAL).as(Boolean.class)
                .or(() -> defaults.authenticationOptional)
                .ifPresent(builder::authenticationOptional);
        config.get(KEY_AUDIT).as(Boolean.class).or(() -> defaults.audited)
                .ifPresent(builder::audit);
        config.get(KEY_AUTHORIZE).as(Boolean.class).or(() -> defaults.authorize)
                .ifPresent(builder::authorize);
        config.get(KEY_AUDIT_EVENT_TYPE).asString().or(() -> defaults.auditEventType)
                .ifPresent(builder::auditEventType);
        config.get(KEY_AUDIT_MESSAGE_FORMAT).asString().or(() -> defaults.auditMessageFormat)
                .ifPresent(builder::auditMessageFormat);

        // now resolve implicit behavior

        // roles allowed implies atn and atz
        if (config.get(KEY_ROLES_ALLOWED).exists()) {
            // we have roles allowed defined
            if (!config.get(KEY_AUTHENTICATE).exists()) {
                builder.authenticate(true);
            }
            if (!config.get(KEY_AUTHORIZE).exists()) {
                builder.authorize(true);
            }
        }

        // optional atn implies atn
        config.get(KEY_AUTHENTICATION_OPTIONAL).as(Boolean.class).ifPresent(aBoolean -> {
            if (aBoolean) {
                if (!config.get(KEY_AUTHENTICATE).exists()) {
                    builder.authenticate(true);
                }
            }
        });

        // explicit atn provider implies atn
        config.get(KEY_AUTHENTICATOR).asString().ifPresent(value -> {
            if (!config.get(KEY_AUTHENTICATE).exists()) {
                builder.authenticate(true);
            }
        });

        // explicit atz provider implies atz
        config.get(KEY_AUTHORIZER).asString().ifPresent(value -> {
            if (!config.get(KEY_AUTHORIZE).exists()) {
                builder.authorize(true);
            }
        });

        return builder.build();
    }

    private static <T> void configure(Config config,
                                      String key,
                                      Optional<T> defaultValue,
                                      Consumer<T> builderMethod,
                                      Class<T> clazz) {
        config.get(key).as(clazz).or(() -> defaultValue).ifPresent(builderMethod);
    }

    static GrpcSecurityHandler create() {
        // constant is OK, object is immutable
        return DEFAULT_INSTANCE;
    }

    private static Builder builder() {
        return new Builder();
    }

    private static Builder builder(GrpcSecurityHandler toCopy) {
        return new Builder().configureFrom(toCopy);
    }

    /**
     * Modifies a {@link io.helidon.grpc.server.ServiceDescriptor.Rules} to add this {@link GrpcSecurityHandler}.
     *
     * @param rules  the {@link io.helidon.grpc.server.ServiceDescriptor.Rules} to modify
     */
    @Override
    public void configure(ServiceDescriptor.Rules rules) {
        rules.addContextValue(GrpcSecurity.GRPC_SECURITY_HANDLER, this);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        Context context = Context.current().withValue(GrpcSecurity.GRPC_SECURITY_HANDLER, this);
        return Contexts.interceptCall(context, call, headers, next);
    }

    /**
     * Perform security checks.
     *
     * @param call     the current gRPC call to check
     * @param headers  the call headers
     * @param next     the next handler in the chain
     * @param <ReqT>   the request type
     * @param <RespT>  the response type
     *
     * @return listener for processing incoming messages for {@code call}, never {@code null}
     */
    <ReqT, RespT> ServerCall.Listener<ReqT> handleSecurity(ServerCall<ReqT, RespT> call,
                                                           Metadata headers,
                                                           ServerCallHandler<ReqT, RespT> next) {
        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get();

        if (securityContext == null) {
            call.close(Status.FAILED_PRECONDITION
                               .withDescription("Security context not present. Maybe you forgot to "
                                                        + "GrpcRouting.builder().intercept(GrpcSecurity.create"
                                                        + "(security))..."), new Metadata());
            return new EmptyListener<>();
        }

        if (combined) {
            return processSecurity(securityContext, call, headers, next);
        } else {
            // the following condition may be met for multiple threads - and we don't really care
            // as the result is exactly the same in all cases and doesn't have side effects
            if (null == combinedHandler.get()) {
                // we may have a default handler configured
                GrpcSecurityHandler defaultHandler = GrpcSecurity.GRPC_SECURITY_HANDLER.get();

                if (defaultHandler == null) {
                    defaultHandler = DEFAULT_INSTANCE;
                }

                // intentional same instance comparison, as I want to prevent endless loop
                //noinspection ObjectEquality
                if (defaultHandler == DEFAULT_INSTANCE) {
                    combinedHandler.set(this);
                } else {
                    combinedHandler.compareAndSet(null,
                                                  builder(defaultHandler).configureFrom(this).combined().build());
                }
            }

            return combinedHandler.get().processSecurity(securityContext, call, headers, next);
        }
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> processSecurity(SecurityContext securityContext,
                                                                    ServerCall<ReqT, RespT> call,
                                                                    Metadata headers,
                                                                    ServerCallHandler<ReqT, RespT> next) {
        SecurityTracing tracing = SecurityTracing.get();
        tracing.securityContext(securityContext);

        securityContext.endpointConfig(securityContext.endpointConfig()
                                               .derive()
                                               .configMap(configMap)
                                               .customObjects(customObjects.orElse(new ClassToInstanceStore<>()))
                                               .build());

        CompletionStage<Boolean> stage = processAuthentication(call, headers, securityContext, tracing.atnTracing())
                .thenCompose(atnResult -> {
                    if (atnResult.proceed) {
                        // authentication was OK or disabled, we should continue
                        return processAuthorization(securityContext, tracing.atzTracing());
                    } else {
                        // authentication told us to stop processing
                        return CompletableFuture.completedFuture(AtxResult.STOP);
                    }
                })
                .thenApply(atzResult -> {
                    if (atzResult.proceed) {
                        // authorization was OK, we can continue processing
                        tracing.logProceed();
                        tracing.finish();
                        return true;
                    } else {
                        tracing.logDeny();
                        tracing.finish();
                        return false;
                    }
                });

        ServerCall.Listener<ReqT> listener;
        CallWrapper<ReqT, RespT> callWrapper = new CallWrapper<>(call);

        try {
            boolean proceed = stage.toCompletableFuture().get();

            if (proceed) {
                listener = next.startCall(callWrapper, headers);
            } else {
                callWrapper.close(Status.PERMISSION_DENIED, new Metadata());
                listener = new EmptyListener<>();
            }
        } catch (Throwable throwable) {
            tracing.error(throwable);
            LOGGER.log(Level.SEVERE, "Unexpected exception during security processing", throwable);
            callWrapper.close(Status.INTERNAL, new Metadata());
            listener = new EmptyListener<>();
        }

        return new AuditingListener<>(listener, callWrapper, headers, securityContext);
    }

    private <ReqT, RespT> void processAudit(ServerCall<ReqT, RespT> call,
                                            Metadata headers,
                                            SecurityContext securityContext,
                                            Status status) {
        // make sure we actually should audit
        if (!audited.orElse(true)) {
            // explicitly disabled
            return;
        }

        AuditEvent.AuditSeverity severity = status.isOk()
                ? AuditEvent.AuditSeverity.SUCCESS
                : AuditEvent.AuditSeverity.FAILURE;

        SecurityAuditEvent auditEvent = SecurityAuditEvent
                .audit(severity,
                       auditEventType.orElse(DEFAULT_AUDIT_EVENT_TYPE),
                       auditMessageFormat.orElse(DEFAULT_AUDIT_MESSAGE_FORMAT))
                .addParam(plain("method", call.getMethodDescriptor().getFullMethodName()))
                .addParam(plain("status", status.getCode()))
                .addParam(plain("subject", securityContext.user().orElse(SecurityContext.ANONYMOUS)))
                .addParam(plain("transport", "grpc"))
                .addParam(plain("resourceType", "grpc"));

        securityContext.service().ifPresent(svc -> auditEvent.addParam(plain("service", svc.toString())));

        securityContext.audit(auditEvent);
    }

    private CompletionStage<AtxResult> processAuthentication(ServerCall<?, ?> call,
                                                             Metadata headers,
                                                             SecurityContext securityContext,
                                                             AtnTracing atnTracing) {
        if (!authenticate.orElse(false)) {
            return CompletableFuture.completedFuture(AtxResult.PROCEED);
        }

        CompletableFuture<AtxResult> future = new CompletableFuture<>();

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = securityContext.atnClientBuilder();

        configureSecurityRequest(clientBuilder,
                                 atnTracing.findParent().orElse(null));

        clientBuilder.explicitProvider(explicitAuthenticator.orElse(null)).submit().thenAccept(response -> {
            switch (response.status()) {
            case SUCCESS:
                //everything is fine, we can continue with processing
                break;
            case FAILURE_FINISH:
                if (atnFinishFailure(future)) {
                    atnSpanFinish(atnTracing, response);
                    return;
                }
                break;
            case SUCCESS_FINISH:
                atnFinish(future);
                atnSpanFinish(atnTracing, response);
                return;
            case ABSTAIN:
            case FAILURE:
                if (atnAbstainFailure(future)) {
                    atnSpanFinish(atnTracing, response);
                    return;
                }
                break;
            default:
                Exception e = new SecurityException("Invalid SecurityStatus returned: " + response.status());
                future.completeExceptionally(e);
                atnTracing.error(e);
                return;
            }

            atnSpanFinish(atnTracing, response);
            future.complete(new AtxResult(clientBuilder.buildRequest()));
        }).exceptionally(throwable -> {
            atnTracing.error(throwable);
            future.completeExceptionally(throwable);
            return null;
        });

        return future;
    }

    private void atnSpanFinish(AtnTracing atnTracing, AuthenticationResponse response) {
        response.user().ifPresent(atnTracing::logUser);
        response.service().ifPresent(atnTracing::logService);

        atnTracing.logStatus(response.status());
        atnTracing.finish();
    }

    private boolean atnAbstainFailure(CompletableFuture<AtxResult> future) {
        if (authenticationOptional.orElse(false)) {
            LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            return false;
        }

        future.complete(AtxResult.STOP);
        return true;
    }

    private boolean atnFinishFailure(CompletableFuture<AtxResult> future) {

        if (authenticationOptional.orElse(false)) {
            LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            return false;
        } else {
            future.complete(AtxResult.STOP);
            return true;
        }
    }

    private void atnFinish(CompletableFuture<AtxResult> future) {
        future.complete(AtxResult.STOP);
    }

    private void configureSecurityRequest(SecurityRequestBuilder<? extends SecurityRequestBuilder<?>> request,
                                          SpanContext parentSpanContext) {

        request.optional(authenticationOptional.orElse(false))
                .tracingSpan(parentSpanContext);
    }

    private CompletionStage<AtxResult> processAuthorization(
            SecurityContext context,
            AtzTracing atzTracing) {
        CompletableFuture<AtxResult> future = new CompletableFuture<>();

        if (!authorize.orElse(false)) {
            future.complete(AtxResult.PROCEED);
            atzTracing.logStatus(SecurityResponse.SecurityStatus.ABSTAIN);
            atzTracing.finish();
            return future;
        }

        Set<String> rolesSet = rolesAllowed.orElse(Set.of());

        if (!rolesSet.isEmpty()) {
            // first validate roles - RBAC is supported out of the box by security, no need to invoke provider
            if (explicitAuthorizer.isPresent()) {
                if (rolesSet.stream().noneMatch(role -> context.isUserInRole(role, explicitAuthorizer.get()))) {
                    future.complete(AtxResult.STOP);
                    atzTracing.finish();
                    return future;
                }
            } else {
                if (rolesSet.stream().noneMatch(context::isUserInRole)) {
                    future.complete(AtxResult.STOP);
                    atzTracing.finish();
                    return future;
                }
            }
        }

        SecurityClientBuilder<AuthorizationResponse> client;

        client = context.atzClientBuilder();
        configureSecurityRequest(client,
                                 atzTracing.findParent().orElse(null));

        client.explicitProvider(explicitAuthorizer.orElse(null)).submit().thenAccept(response -> {
            atzTracing.logStatus(response.status());
            switch (response.status()) {
            case SUCCESS:
                //everything is fine, we can continue with processing
                break;
            case FAILURE_FINISH:
            case SUCCESS_FINISH:
                atzTracing.finish();
                future.complete(AtxResult.STOP);
                return;
            case ABSTAIN:
            case FAILURE:
                atzTracing.finish();
                future.complete(AtxResult.STOP);
                return;
            default:
                SecurityException e = new SecurityException("Invalid SecurityStatus returned: " + response.status());
                atzTracing.error(e);
                future.completeExceptionally(e);
                return;
            }

            atzTracing.finish();
            // everything was OK
            future.complete(AtxResult.PROCEED);
        }).exceptionally(throwable -> {
            atzTracing.error(throwable);
            future.completeExceptionally(throwable);
            return null;
        });

        return future;
    }

    /**
     * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
     * Will enable authentication.
     *
     * @param explicitAuthenticator name of authenticator as configured in {@link Security}
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler authenticator(String explicitAuthenticator) {
        return builder(this).authenticator(explicitAuthenticator).build();
    }

    /**
     * Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
     * permitted).
     * Will enable authorization.
     *
     * @param explicitAuthorizer name of authorizer as configured in {@link Security}
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler authorizer(String explicitAuthorizer) {
        return builder(this).authorizer(explicitAuthorizer).build();
    }

    /**
     * An array of allowed roles for this path - must have a security provider supporting roles (either authentication
     * or authorization provider).
     * This method enables authentication and authorization (you can disable them again by calling
     * {@link GrpcSecurityHandler#skipAuthorization()}
     * and {@link #skipAuthentication()} if needed).
     *
     * @param roles if subject is any of these roles, allow access
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler rolesAllowed(String... roles) {
        return builder(this).rolesAllowed(roles).authorize(true).authenticate(true).build();

    }

    /**
     * If called, authentication failure will not abort request and will continue as anonymous (authentication is not optional
     * by default).
     * Will enable authentication.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler authenticationOptional() {
        return builder(this).authenticationOptional(true).build();
    }

    /**
     * If called, request will go through authentication process - (authentication is disabled by default - it may be enabled
     * as a side effect of other methods, such as {@link #rolesAllowed(String...)}.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler authenticate() {
        return builder(this).authenticate(true).build();
    }

    /**
     * If called, request will NOT go through authentication process. Use this when another method implies authentication
     * (such as {@link #rolesAllowed(String...)}) and yet it is not desired (e.g. everything is handled by authorization).
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler skipAuthentication() {
        return builder(this).authenticate(false).build();
    }

    /**
     * Register a custom object for security request(s).
     * This creates a hard dependency on a specific security provider, so use with care.
     *
     * @param object An object expected by security provider
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler customObject(Object object) {
        return builder(this).customObject(object).build();
    }

    /**
     * Override for event-type, defaults to {@value #DEFAULT_AUDIT_EVENT_TYPE}.
     *
     * @param eventType audit event type to use
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler auditEventType(String eventType) {
        return builder(this).auditEventType(eventType).build();
    }

    /**
     * Override for audit message format, defaults to {@value #DEFAULT_AUDIT_MESSAGE_FORMAT}.
     *
     * @param messageFormat audit message format to use
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler auditMessageFormat(String messageFormat) {
        return builder(this).auditMessageFormat(messageFormat).build();
    }

    /**
     * If called, request will go through authorization process - (authorization is disabled by default - it may be enabled
     * as a side effect of other methods, such as {@link #rolesAllowed(String...)}.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler authorize() {
        return builder(this).authorize(true).build();
    }

    /**
     * Skip authorization for this route.
     * Use this when authorization is implied by another method on this class (e.g. {@link #rolesAllowed(String...)} and
     * you want to explicitly forbid it.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler skipAuthorization() {
        return builder(this).authorize(false).build();
    }

    /**
     * Audit this request for any method. Request is audited with event type {@link #DEFAULT_AUDIT_EVENT_TYPE}.
     * <p>
     * By default audit is enabled as follows (based on HTTP methods):
     * <ul>
     * <li>GET, HEAD - not audited</li>
     * <li>PUT, POST, DELETE - audited</li>
     * <li>any other method (e.g. custom methods) - audited</li>
     * </ul>
     * Calling this method will override the default setting and audit any method this handler is registered for.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler audit() {
        return builder(this).audit(true).build();
    }

    /**
     * Disable auditing of this request. Will override defaults and disable auditing for all methods this handler is registered
     * for.
     * <p>
     * By default audit is enabled as follows (based on HTTP methods):
     * <ul>
     * <li>GET, HEAD - not audited</li>
     * <li>PUT, POST, DELETE - audited</li>
     * <li>any other method (e.g. custom methods) - audited</li>
     * </ul>
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public GrpcSecurityHandler skipAudit() {
        return builder(this).audit(false).build();
    }

    /**
     * Obtain the roles allowed for this {@link GrpcSecurityHandler}.
     *
     * @return  an {@link Optional} containing the the roles allowed for
     *          this {@link GrpcSecurityHandler} if any have been configured
     */
    Optional<Set<String>> getRolesAllowed() {
        return rolesAllowed.map(Collections::unmodifiableSet);
    }

    /**
     * Obtain the explicit authenticator for this {@link GrpcSecurityHandler}.
     *
     * @return  an {@link Optional} containing the the explicit authenticator for
     *          this {@link GrpcSecurityHandler} if any have been configured
     */
    Optional<String> getExplicitAuthenticator() {
        return explicitAuthenticator;
    }

    /**
     * Obtain the explicit authorizer for this {@link GrpcSecurityHandler}.
     *
     * @return  an {@link Optional} containing the the explicit authorizer for
     *          this {@link GrpcSecurityHandler} if any have been configured
     */
    Optional<String> getExplicitAuthorizer() {
        return explicitAuthorizer;
    }

    /**
     * Obtain whether this {@link GrpcSecurityHandler} performs authentication.
     *
     * @return  an {@link Optional} containing {@code true} if  this
     *          {@link GrpcSecurityHandler} performs authentication
     */
    Optional<Boolean> isAuthenticate() {
        return authenticate;
    }

    /**
     * Obtain whether this {@link GrpcSecurityHandler} allows anonymous access.
     *
     * @return  an {@link Optional} containing {@code true} if  this
     *          {@link GrpcSecurityHandler} allows anonymous access
     */
    Optional<Boolean> isAuthenticationOptional() {
        return authenticationOptional;
    }

    /**
     * Obtain whether this {@link GrpcSecurityHandler} performs authorization.
     *
     * @return  an {@link Optional} containing {@code true} if  this
     *          {@link GrpcSecurityHandler} performs authorization
     */
    Optional<Boolean> isAuthorize() {
        return authorize;
    }

    /**
     * Obtain whether this {@link GrpcSecurityHandler} audits security operations.
     *
     * @return  an {@link Optional} containing {@code true} if  this
     *          {@link GrpcSecurityHandler} audits security operations
     */
    Optional<Boolean> isAudited() {
        return audited;
    }

    /**
     * Obtain the audit event type override.
     *
     * @return  an {@link Optional} containing the audit event type
     *          override if one has been set
     */
    Optional<String> getAuditEventType() {
        return auditEventType;
    }

    /**
     * Obtain the audit message format override.
     *
     * @return  an {@link Optional} containing the audit message format
     *          override if one has been set
     */
    Optional<String> getAuditMessageFormat() {
        return auditMessageFormat;
    }

    private static final class AtxResult {
        private static final AtxResult PROCEED = new AtxResult(true);
        private static final AtxResult STOP = new AtxResult(false);

        private final boolean proceed;

        private AtxResult(boolean proceed) {
            this.proceed = proceed;
        }

        @SuppressWarnings("unused")
        private AtxResult(SecurityRequest ignored) {
            this.proceed = true;
        }
    }

    // WARNING: builder methods must not have side-effects, as they are used to build instance from configuration
    // if you want side effects, use methods on GrpcSecurityInterceptor
    private static final class Builder implements io.helidon.common.Builder<GrpcSecurityHandler> {
        private Optional<Set<String>> rolesAllowed = Optional.empty();
        private Optional<ClassToInstanceStore<Object>> customObjects = Optional.empty();
        private Optional<Config> config = Optional.empty();
        private Optional<String> explicitAuthenticator = Optional.empty();
        private Optional<String> explicitAuthorizer = Optional.empty();
        private Optional<Boolean> authenticate = Optional.empty();
        private Optional<Boolean> authenticationOptional = Optional.empty();
        private Optional<Boolean> authorize = Optional.empty();
        private Optional<Boolean> audited = Optional.empty();
        private Optional<String> auditEventType = Optional.empty();
        private Optional<String> auditMessageFormat = Optional.empty();
        private boolean combined;

        private Builder() {
        }

        @Override
        public GrpcSecurityHandler build() {
            return new GrpcSecurityHandler(this);
        }

        private Builder combined() {
            this.combined = true;

            return this;
        }

        // add to this builder
        private Builder configureFrom(GrpcSecurityHandler handler) {
            handler.rolesAllowed.ifPresent(this::rolesAllowed);
            handler.customObjects.ifPresent(this::customObjects);
            handler.config.ifPresent(this::config);
            handler.explicitAuthenticator.ifPresent(this::authenticator);
            handler.explicitAuthorizer.ifPresent(this::authorizer);
            handler.authenticate.ifPresent(this::authenticate);
            handler.authenticationOptional.ifPresent(this::authenticationOptional);
            handler.audited.ifPresent(this::audit);
            handler.auditEventType.ifPresent(this::auditEventType);
            handler.auditMessageFormat.ifPresent(this::auditMessageFormat);
            handler.authorize.ifPresent(this::authorize);

            return this;
        }

        private Builder customObjects(ClassToInstanceStore<Object> store) {
            customObjects
                    .ifPresentOrElse(myStore -> myStore.putAll(store), () -> {
                        ClassToInstanceStore<Object> ctis = new ClassToInstanceStore<>();
                        ctis.putAll(store);
                        this.customObjects = Optional.of(ctis);
                    });

            return this;
        }

        /**
         * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
         *
         * @param explicitAuthenticator name of authenticator as configured in {@link Security}
         * @return updated builder instance
         */
        Builder authenticator(String explicitAuthenticator) {
            this.explicitAuthenticator = Optional.of(explicitAuthenticator);
            return this;
        }

        /**
         * Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
         * permitted).
         *
         * @param explicitAuthorizer name of authorizer as configured in {@link Security}
         * @return updated builder instance
         */
        Builder authorizer(String explicitAuthorizer) {
            this.explicitAuthorizer = Optional.of(explicitAuthorizer);
            return this;
        }

        /**
         * An array of allowed roles for this path - must have a security provider supporting roles.
         *
         * @param roles if subject is any of these roles, allow access
         * @return updated builder instance
         */
        Builder rolesAllowed(String... roles) {
            return rolesAllowed(Arrays.asList(roles));
        }

        private Builder config(Config config) {
            this.config = Optional.of(config);
            return this;
        }

        /**
         * If called, authentication failure will not abort request and will continue as anonymous (defaults to false).
         *
         * @param isOptional whether authn is optional
         * @return updated builder instance
         */
        Builder authenticationOptional(boolean isOptional) {
            this.authenticationOptional = Optional.of(isOptional);
            return this;
        }

        /**
         * If called, request will go through authentication process - defaults to false (even if authorize is true).
         *
         * @param authenticate whether to authenticate or not
         * @return updated builder instance
         */
        Builder authenticate(boolean authenticate) {
            this.authenticate = Optional.of(authenticate);
            return this;
        }

        /**
         * Register a custom object for security request(s).
         * This creates a hard dependency on a specific security provider, so use with care.
         *
         * @param object An object expected by security provider
         * @return updated builder instance
         */
        Builder customObject(Object object) {
            customObjects
                    .ifPresentOrElse(store -> store.putInstance(object), () -> {
                        ClassToInstanceStore<Object> ctis = new ClassToInstanceStore<>();
                        ctis.putInstance(object);
                        customObjects = Optional.of(ctis);
                    });

            return this;
        }

        /**
         * Override for event-type, defaults to {@value #DEFAULT_AUDIT_EVENT_TYPE}.
         *
         * @param eventType audit event type to use
         * @return updated builder instance
         */
        Builder auditEventType(String eventType) {
            this.auditEventType = Optional.of(eventType);
            return this;
        }

        /**
         * Override for audit message format, defaults to {@value #DEFAULT_AUDIT_MESSAGE_FORMAT}.
         *
         * @param messageFormat audit message format to use
         * @return updated builder instance
         */
        Builder auditMessageFormat(String messageFormat) {
            this.auditMessageFormat = Optional.of(messageFormat);
            return this;
        }

        /**
         * Enable authorization for this route.
         *
         * @param authorize whether to authorize
         * @return updated builder instance
         */
        Builder authorize(boolean authorize) {
            this.authorize = Optional.of(authorize);
            return this;
        }

        /**
         * Whether to audit this request - defaults to false, if enabled, request is audited with event type "request".
         *
         * @return updated builder instance
         */
        Builder audit(boolean audited) {
            this.audited = Optional.of(audited);
            return this;
        }

        Builder rolesAllowed(Collection<String> roles) {
            rolesAllowed.ifPresentOrElse(strings -> strings.addAll(roles),
                                                              () -> {
                                                                  Set<String> newRoles = new HashSet<>(roles);
                                                                  rolesAllowed = Optional.of(newRoles);
                                                              });
            return this;
        }
    }

    /**
     * An empty {@link ServerCall.Listener} used to terminate a call if
     * authentication fails.
     *
     * @param <T> the type of the call
     */
    static class EmptyListener<T> extends ServerCall.Listener<T> {
    }

    /**
     * A logging {@link ServerCall.Listener}.
     *
     * @param <ReqT> the request type
     */
    private class AuditingListener<ReqT, RespT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private CallWrapper<ReqT, RespT> call;
        private Metadata headers;
        private SecurityContext securityContext;

        private AuditingListener(ServerCall.Listener<ReqT> delegate,
                                CallWrapper<ReqT, RespT> call,
                                Metadata headers,
                                SecurityContext securityContext) {
            super(delegate);
            this.call = call;
            this.headers = headers;
            this.securityContext = securityContext;
        }

        @Override
        public void onCancel() {
            processAudit(call, headers, securityContext, call.getCloseStatus());
        }

        @Override
        public void onComplete() {
            processAudit(call, headers, securityContext, call.getCloseStatus());
        }
    }

    private class CallWrapper<ReqT, RespT>
            extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
        private Status closeStatus;

        private CallWrapper(ServerCall<ReqT, RespT> delegate) {
            super(delegate);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            closeStatus = status;
            super.close(status, trailers);
        }

        Status getCloseStatus() {
            return closeStatus;
        }
    }
}

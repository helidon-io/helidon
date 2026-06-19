/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc.security;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.security.AuditEvent;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ClassToInstanceStore;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityRequest;
import io.helidon.security.SecurityRequestBuilder;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.AtnTracing;
import io.helidon.security.integration.common.AtzTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.tracing.SpanContext;
import io.helidon.webserver.grpc.GrpcMethodDescriptor;
import io.helidon.webserver.grpc.GrpcServiceDescriptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import static io.helidon.security.AuditEvent.AuditParam.plain;

/**
 * Per-service or per-method security rules for gRPC security.
 * <p>
 * Register {@link GrpcSecurity} on gRPC routing to enforce authentication, authorization, and auditing.
 * A handler attached directly to a gRPC service or method descriptor only selects the rules used by
 * {@code GrpcSecurity}; by itself it does not enforce security.
 */
@Weight(InterceptorWeights.CONTEXT)
@SuppressWarnings("rawtypes")
public final class GrpcSecurityHandler implements ServerInterceptor,
        GrpcServiceDescriptor.Configurer,
        GrpcMethodDescriptor.Configurer,
        RuntimeType.Api<GrpcSecurityHandlerConfig> {
    static final String DEFAULT_AUDIT_EVENT_TYPE = "grpcRequest";
    static final String DEFAULT_AUDIT_MESSAGE_FORMAT = "%2$s %1$s %4$s %5$s requested by %3$s";
    private static final System.Logger LOGGER = System.getLogger(GrpcSecurityHandler.class.getName());
    private static final GrpcSecurityHandler DEFAULT_INSTANCE = builder().build();

    private final GrpcSecurityHandlerConfig config;
    private final Optional<Set<String>> rolesAllowed;
    private final List<SecurityLevel> securityLevels;
    private final Optional<ClassToInstanceStore<Object>> customObjects;
    private final Optional<String> explicitAuthenticator;
    private final Optional<String> explicitAuthorizer;
    private final Optional<Boolean> authenticate;
    private final Optional<Boolean> authenticationOptional;
    private final Optional<Boolean> authorize;
    private final Optional<Boolean> audited;
    private final Optional<String> auditEventType;
    private final Optional<String> auditMessageFormat;
    private final Map<String, Config> configMap = new HashMap<>();
    private final AtomicReference<CombinedHandler> combinedHandler = new AtomicReference<>();

    private GrpcSecurityHandler(GrpcSecurityHandlerConfig config) {
        this.config = config;

        Set<String> rolesAllowedSet = config.rolesAllowed();
        if (rolesAllowedSet.isEmpty()) {
            this.rolesAllowed = Optional.empty();
        } else {
            this.rolesAllowed = Optional.of(rolesAllowedSet);
        }

        this.securityLevels = config.securityLevels();
        this.customObjects = config.customObjects()
                .map(it -> {
                    ClassToInstanceStore<Object> ctis = ClassToInstanceStore.create();
                    ctis.putAll(it);
                    return ctis;
                });
        this.explicitAuthenticator = config.authenticator();
        this.explicitAuthorizer = config.authorizer();
        this.authenticate = config.authenticate();
        this.authenticationOptional = config.authenticationOptional();
        this.authorize = config.authorize();
        this.audited = config.audit();
        this.auditEventType = config.auditEventType();
        this.auditMessageFormat = config.auditMessageFormat();

        config.config().ifPresent(conf -> conf.asNodeList().get().forEach(node -> configMap.put(node.name(), node)));
    }

    /**
     * Create a new fluent API builder for security handler.
     *
     * @return a new builder
     */
    public static GrpcSecurityHandlerConfig.Builder builder() {
        return GrpcSecurityHandlerConfig.builder();
    }

    /**
     * Create a new handler, customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return configured handler
     */
    public static GrpcSecurityHandler create(Consumer<GrpcSecurityHandlerConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a handler from configuration and defaults.
     *
     * @param config configuration
     * @param defaults defaults
     * @return configured handler
     */
    public static GrpcSecurityHandler create(Config config, GrpcSecurityHandler defaults) {
        return builder()
                .from(defaults.prototype())
                .config(config)
                .build();
    }

    /**
     * Create a handler from configuration.
     *
     * @param config configuration
     * @return configured handler
     */
    public static GrpcSecurityHandler create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    static GrpcSecurityHandler create(GrpcSecurityHandlerConfig config) {
        return new GrpcSecurityHandler(config);
    }

    static GrpcSecurityHandler create() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public GrpcSecurityHandlerConfig prototype() {
        return config;
    }

    @Override
    public void configure(GrpcServiceDescriptor.Rules rules) {
        rules.addContextValue(GrpcSecurity.GRPC_SERVICE_SECURITY_HANDLER, this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(GrpcMethodDescriptor.Rules rules) {
        rules.addContextValue(GrpcSecurity.GRPC_METHOD_SECURITY_HANDLER, this);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        Context context = Context.current().withValue(GrpcSecurity.GRPC_SECURITY_HANDLER, this);
        return Contexts.interceptCall(context, call, headers, next);
    }

    /**
     * Use a named authenticator.
     *
     * @param explicitAuthenticator authenticator name
     * @return new handler
     */
    public GrpcSecurityHandler authenticator(String explicitAuthenticator) {
        return builder().from(prototype()).authenticator(explicitAuthenticator).build();
    }

    /**
     * Use a named authorizer.
     *
     * @param explicitAuthorizer authorizer name
     * @return new handler
     */
    public GrpcSecurityHandler authorizer(String explicitAuthorizer) {
        return builder().from(prototype()).authorizer(explicitAuthorizer).build();
    }

    /**
     * Require at least one of the specified roles.
     *
     * @param roles allowed roles
     * @return new handler
     */
    public GrpcSecurityHandler rolesAllowed(String... roles) {
        return builder()
                .from(prototype())
                .rolesAllowed(Set.of(roles))
                .authorize(true)
                .authenticate(true)
                .build();
    }

    /**
     * Add a security level discovered from endpoint annotations.
     *
     * @param securityLevel security level
     * @return new handler
     */
    public GrpcSecurityHandler securityLevel(SecurityLevel securityLevel) {
        Objects.requireNonNull(securityLevel);
        return builder().from(prototype()).addSecurityLevel(securityLevel).build();
    }

    /**
     * Allow anonymous access when authentication fails.
     *
     * @return new handler
     */
    public GrpcSecurityHandler authenticationOptional() {
        return builder().from(prototype()).authenticationOptional(true).build();
    }

    /**
     * Authenticate a request.
     *
     * @return new handler
     */
    public GrpcSecurityHandler authenticate() {
        return builder().from(prototype()).authenticate(true).build();
    }

    /**
     * Skip authentication.
     *
     * @return new handler
     */
    public GrpcSecurityHandler skipAuthentication() {
        return builder().from(prototype()).authenticate(false).build();
    }

    /**
     * Register a custom object for security request(s).
     *
     * @param object object expected by a security provider
     * @return new handler
     */
    public GrpcSecurityHandler customObject(Object object) {
        return builder().from(prototype()).addObject(object).build();
    }

    /**
     * Override audit event type.
     *
     * @param eventType event type
     * @return new handler
     */
    public GrpcSecurityHandler auditEventType(String eventType) {
        return builder().from(prototype()).auditEventType(eventType).build();
    }

    /**
     * Override audit message format.
     *
     * @param messageFormat message format
     * @return new handler
     */
    public GrpcSecurityHandler auditMessageFormat(String messageFormat) {
        return builder().from(prototype()).auditMessageFormat(messageFormat).build();
    }

    /**
     * Authorize a request.
     *
     * @return new handler
     */
    public GrpcSecurityHandler authorize() {
        return builder().from(prototype()).authorize(true).build();
    }

    /**
     * Skip authorization.
     *
     * @return new handler
     */
    public GrpcSecurityHandler skipAuthorization() {
        return builder().from(prototype()).authorize(false).build();
    }

    /**
     * Audit a request.
     *
     * @return new handler
     */
    public GrpcSecurityHandler audit() {
        return builder().from(prototype()).audit(true).build();
    }

    /**
     * Disable auditing.
     *
     * @return new handler
     */
    public GrpcSecurityHandler skipAudit() {
        return builder().from(prototype()).audit(false).build();
    }

    GrpcSecurityHandler combine(GrpcSecurityHandler defaults) {
        Objects.requireNonNull(defaults);
        if (defaults == DEFAULT_INSTANCE || defaults == this) {
            return this;
        }

        CombinedHandler cached = combinedHandler.get();
        if (cached != null && cached.defaults == defaults) {
            return cached.handler;
        }

        GrpcSecurityHandlerConfig.Builder builder = builder();
        defaults.update(builder);
        update(builder);
        GrpcSecurityHandler handler = builder.build();
        combinedHandler.compareAndSet(null, new CombinedHandler(defaults, handler));

        cached = combinedHandler.get();
        return cached.defaults == defaults ? cached.handler : handler;
    }

    private void update(GrpcSecurityHandlerConfig.Builder builder) {
        rolesAllowed.ifPresent(builder::rolesAllowed);
        if (!securityLevels.isEmpty()) {
            builder.securityLevels(securityLevels);
        }
        explicitAuthenticator.ifPresent(builder::authenticator);
        explicitAuthorizer.ifPresent(builder::authorizer);
        authenticate.ifPresent(builder::authenticate);
        authenticationOptional.ifPresent(builder::authenticationOptional);
        authorize.ifPresent(builder::authorize);
        audited.ifPresent(builder::audit);
        auditEventType.ifPresent(builder::auditEventType);
        auditMessageFormat.ifPresent(builder::auditMessageFormat);
        customObjects.ifPresent(builder::customObjects);
        config.config().ifPresent(builder::config);
    }

    <ReqT, RespT> ServerCall.Listener<ReqT> handleSecurity(ServerCall<ReqT, RespT> call,
                                                           Metadata headers,
                                                           ServerCallHandler<ReqT, RespT> next) {
        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get();

        if (securityContext == null) {
            call.close(Status.FAILED_PRECONDITION
                               .withDescription("Security context not present. Configure GrpcSecurity on the gRPC routing."),
                       new Metadata());
            return new EmptyListener<>();
        }

        return processSecurity(securityContext, call, headers, next);
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> processSecurity(SecurityContext securityContext,
                                                                    ServerCall<ReqT, RespT> call,
                                                                    Metadata headers,
                                                                    ServerCallHandler<ReqT, RespT> next) {
        SecurityTracing tracing = SecurityTracing.get();
        tracing.securityContext(securityContext);

        securityContext.endpointConfig(securityContext.endpointConfig()
                                               .derive()
                                               .securityLevels(securityLevels)
                                               .configMap(configMap)
                                               .customObjects(customObjects.orElseGet(ClassToInstanceStore::create))
                                               .build());

        CallWrapper<ReqT, RespT> callWrapper = new CallWrapper<>(call);

        try {
            AtxResult atnResult = processAuthentication(callWrapper, securityContext, tracing.atnTracing());
            AtxResult atzResult = atnResult.proceed
                    ? processAuthorization(callWrapper, securityContext, tracing.atzTracing())
                    : AtxResult.STOP;

            AtomicBoolean auditSubmitted = new AtomicBoolean();
            ServerCall.Listener<ReqT> listener;
            if (atzResult.proceed) {
                tracing.logProceed();
                tracing.finish();
                listener = next.startCall(callWrapper, headers);
                if (callWrapper.closeStatus() != null) {
                    submitAuditOnce(auditSubmitted, callWrapper, securityContext, callWrapper.closeStatus());
                }
            } else {
                tracing.logDeny();
                tracing.finish();
                submitAuditOnce(auditSubmitted, callWrapper, securityContext, callWrapper.closeStatus());
                return new EmptyListener<>();
            }

            return new AuditingListener<>(listener, callWrapper, securityContext, auditSubmitted);
        } catch (Exception e) {
            tracing.error(e);
            LOGGER.log(System.Logger.Level.ERROR, "Unexpected exception during security processing", e);
            callWrapper.close(Status.INTERNAL, new Metadata());
            processAudit(callWrapper, securityContext, callWrapper.closeStatus());
            return new EmptyListener<>();
        }
    }

    private <ReqT, RespT> void submitAuditOnce(AtomicBoolean auditSubmitted,
                                               ServerCall<ReqT, RespT> call,
                                               SecurityContext securityContext,
                                               Status status) {
        if (auditSubmitted.compareAndSet(false, true)) {
            processAudit(call, securityContext, status);
        }
    }

    private <ReqT, RespT> void processAudit(ServerCall<ReqT, RespT> call,
                                            SecurityContext securityContext,
                                            Status status) {
        if (!audited.orElse(true)) {
            return;
        }

        Status auditStatus = status == null ? Status.OK : status;
        AuditEvent.AuditSeverity severity = auditStatus.isOk()
                ? AuditEvent.AuditSeverity.SUCCESS
                : AuditEvent.AuditSeverity.FAILURE;

        SecurityAuditEvent auditEvent = SecurityAuditEvent
                .audit(severity,
                       auditEventType.orElse(DEFAULT_AUDIT_EVENT_TYPE),
                       auditMessageFormat.orElse(DEFAULT_AUDIT_MESSAGE_FORMAT))
                .addParam(plain("method", call.getMethodDescriptor().getFullMethodName()))
                .addParam(plain("status", auditStatus.getCode()))
                .addParam(plain("subject", securityContext.user().orElse(SecurityContext.ANONYMOUS)))
                .addParam(plain("transport", "grpc"))
                .addParam(plain("resourceType", "grpc"));

        securityContext.service().ifPresent(svc -> auditEvent.addParam(plain("service", svc.toString())));

        securityContext.audit(auditEvent);
    }

    private AtxResult processAuthentication(ServerCall<?, ?> call,
                                            SecurityContext securityContext,
                                            AtnTracing atnTracing) {
        if (!authenticate.orElse(false)) {
            return AtxResult.PROCEED;
        }

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = securityContext.atnClientBuilder();
        configureSecurityRequest(clientBuilder,
                                 atnTracing.findParent().orElse(null));

        try {
            AuthenticationResponse response = clientBuilder.explicitProvider(explicitAuthenticator.orElse(null)).submit();

            switch (response.status()) {
            case SUCCESS:
                break;
            case FAILURE_FINISH:
                if (authenticationOptional.orElse(false)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
                    break;
                }
                close(call, response, Status.UNAUTHENTICATED);
                atnSpanFinish(atnTracing, response);
                return AtxResult.STOP;
            case SUCCESS_FINISH:
                close(call, response, Status.OK);
                atnSpanFinish(atnTracing, response);
                return AtxResult.STOP;
            case ABSTAIN:
            case FAILURE:
                if (authenticationOptional.orElse(false)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
                    break;
                }
                close(call, response, Status.UNAUTHENTICATED);
                atnSpanFinish(atnTracing, response);
                return AtxResult.STOP;
            default:
                throw new io.helidon.security.SecurityException("Invalid SecurityStatus returned: " + response.status());
            }

            atnSpanFinish(atnTracing, response);
            return new AtxResult(clientBuilder.buildRequest());
        } catch (Exception e) {
            atnTracing.error(e);
            throw e;
        }
    }

    private void atnSpanFinish(AtnTracing atnTracing, AuthenticationResponse response) {
        response.user().ifPresent(atnTracing::logUser);
        response.service().ifPresent(atnTracing::logService);

        atnTracing.logStatus(response.status());
        atnTracing.finish();
    }

    private void configureSecurityRequest(SecurityRequestBuilder<? extends SecurityRequestBuilder<?>> request,
                                          SpanContext parentSpanContext) {

        request.optional(authenticationOptional.orElse(false))
                .tracingSpan(parentSpanContext);
    }

    private AtxResult processAuthorization(ServerCall<?, ?> call,
                                           SecurityContext context,
                                           AtzTracing atzTracing) {

        if (!authorize.orElse(false)) {
            atzTracing.logStatus(SecurityResponse.SecurityStatus.ABSTAIN);
            atzTracing.finish();
            return AtxResult.PROCEED;
        }

        Set<String> rolesSet = rolesAllowed.orElse(Set.of());
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();

        if (!rolesSet.isEmpty()) {
            if (explicitAuthorizer.isPresent()) {
                if (rolesSet.stream().noneMatch(role -> context.isUserInRole(role, explicitAuthorizer.get()))) {
                    auditRoleMissing(context, fullMethodName, context.user(), rolesSet);
                    call.close(Status.PERMISSION_DENIED, new Metadata());
                    atzTracing.finish();
                    return AtxResult.STOP;
                }
            } else {
                if (rolesSet.stream().noneMatch(context::isUserInRole)) {
                    auditRoleMissing(context, fullMethodName, context.user(), rolesSet);
                    call.close(Status.PERMISSION_DENIED, new Metadata());
                    atzTracing.finish();
                    return AtxResult.STOP;
                }
            }
        }

        SecurityClientBuilder<AuthorizationResponse> client = context.atzClientBuilder();
        configureSecurityRequest(client,
                                 atzTracing.findParent().orElse(null));

        try {
            AuthorizationResponse response = client.explicitProvider(explicitAuthorizer.orElse(null)).submit();
            atzTracing.logStatus(response.status());

            switch (response.status()) {
            case SUCCESS:
                break;
            case SUCCESS_FINISH:
                atzTracing.finish();
                close(call, response, Status.OK);
                return AtxResult.STOP;
            case FAILURE_FINISH:
            case ABSTAIN:
            case FAILURE:
                atzTracing.finish();
                close(call, response, Status.PERMISSION_DENIED);
                return AtxResult.STOP;
            default:
                throw new io.helidon.security.SecurityException("Invalid SecurityStatus returned: " + response.status());
            }

            atzTracing.finish();
            return AtxResult.PROCEED;
        } catch (Exception e) {
            atzTracing.error(e);
            throw e;
        }
    }

    private void auditRoleMissing(SecurityContext context,
                                  String methodName,
                                  Optional<Subject> user,
                                  Set<String> rolesSet) {

        context.audit(SecurityAuditEvent.failure(AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                                                 "User is not in any of the required roles: %s. Method %s. Subject %s")
                              .addParam(AuditEvent.AuditParam.plain("roles", rolesSet))
                              .addParam(AuditEvent.AuditParam.plain("method", methodName))
                              .addParam(AuditEvent.AuditParam.plain("subject", user)));
    }

    private static void close(ServerCall<?, ?> call, SecurityResponse response, Status defaultStatus) {
        Status status = response.statusCode()
                .stream()
                .mapToObj(code -> status(code, defaultStatus))
                .findFirst()
                .orElse(defaultStatus);
        status = response.description()
                .map(status::withDescription)
                .orElse(status);

        call.close(status, trailers(response));
    }

    private static Status status(int httpStatusCode, Status defaultStatus) {
        return switch (httpStatusCode) {
        case 200 -> Status.OK;
        case 400 -> Status.INVALID_ARGUMENT;
        case 401 -> Status.UNAUTHENTICATED;
        case 403 -> Status.PERMISSION_DENIED;
        case 404 -> Status.NOT_FOUND;
        case 409 -> Status.ABORTED;
        case 429 -> Status.RESOURCE_EXHAUSTED;
        case 499 -> Status.CANCELLED;
        case 500 -> Status.INTERNAL;
        case 501 -> Status.UNIMPLEMENTED;
        case 503 -> Status.UNAVAILABLE;
        case 504 -> Status.DEADLINE_EXCEEDED;
        default -> defaultStatus;
        };
    }

    private static Metadata trailers(SecurityResponse response) {
        Metadata trailers = new Metadata();
        for (Map.Entry<String, List<String>> entry : response.responseHeaders().entrySet()) {
            String headerName = entry.getKey().toLowerCase(Locale.ROOT);
            if (headerName.endsWith("-bin")) {
                continue;
            }
            Metadata.Key<String> metadataKey = Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER);
            entry.getValue().forEach(value -> trailers.put(metadataKey, value));
        }
        return trailers;
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

    /**
     * An empty {@link io.grpc.ServerCall.Listener} used to terminate a call if security rejects it.
     *
     * @param <T> listener type
     */
    static class EmptyListener<T> extends ServerCall.Listener<T> {
    }

    private class AuditingListener<ReqT, RespT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final CallWrapper<ReqT, RespT> call;
        private final SecurityContext securityContext;
        private final AtomicBoolean auditSubmitted;

        private AuditingListener(ServerCall.Listener<ReqT> delegate,
                                 CallWrapper<ReqT, RespT> call,
                                 SecurityContext securityContext,
                                 AtomicBoolean auditSubmitted) {
            super(delegate);
            this.call = call;
            this.securityContext = securityContext;
            this.auditSubmitted = auditSubmitted;
        }

        @Override
        public void onCancel() {
            try {
                super.onCancel();
            } finally {
                submitAuditOnce(auditSubmitted,
                                call,
                                securityContext,
                                call.closeStatus() == null ? Status.CANCELLED : call.closeStatus());
            }
        }

        @Override
        public void onComplete() {
            try {
                super.onComplete();
            } finally {
                submitAuditOnce(auditSubmitted, call, securityContext, call.closeStatus());
            }
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

        Status closeStatus() {
            return closeStatus;
        }
    }

    private record CombinedHandler(GrpcSecurityHandler defaults, GrpcSecurityHandler handler) {
    }
}

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

package io.helidon.security.webserver;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Flow;
import io.helidon.config.Config;
import io.helidon.security.AuditEvent;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ClassToInstanceStore;
import io.helidon.security.Entity;
import io.helidon.security.QueryParamMapping;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityRequest;
import io.helidon.security.SecurityRequestBuilder;
import io.helidon.security.SecurityResponse;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.util.TokenHandler;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import static io.helidon.security.AuditEvent.AuditParam.plain;

/**
 * Handles security for web server. This handler is registered either by hand on router config,
 * or automatically from configuration when integration done through {@link WebSecurity#from(Config)}
 * or {@link WebSecurity#from(Security, Config)}.
 */
// we need to have all fields optional and this is cleaner than checking for null
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class SecurityHandler implements Handler {
    private static final Logger LOGGER = Logger.getLogger(SecurityHandler.class.getName());
    private static final String KEY_ROLES_ALLOWED = "roles-allowed";
    private static final String KEY_AUTHENTICATOR = "authenticator";
    private static final String KEY_AUTHORIZER = "authorizer";
    private static final String KEY_AUTHENTICATE = "authenticate";
    private static final String KEY_AUTHENTICATION_OPTIONAL = "authentication-optional";
    private static final String KEY_AUTHORIZE = "authorize";
    private static final String KEY_AUDIT = "audit";
    private static final String KEY_AUDIT_EVENT_TYPE = "audit-event-type";
    private static final String KEY_AUDIT_MESSAGE_FORMAT = "audit-message-format";
    private static final String KEY_QUERY_PARAM_HANDLERS = "query-params";
    private static final String DEFAULT_AUDIT_EVENT_TYPE = "request";
    private static final String DEFAULT_AUDIT_MESSAGE_FORMAT = "%3$s %1$s \"%2$s\" %5$s %6$s requested by %4$s";
    private static final SecurityHandler DEFAULT_INSTANCE = builder().build();

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
    private final List<QueryParamHandler> queryParamHandlers = new LinkedList<>();
    private final boolean combined;
    private final Map<String, Config> configMap = new HashMap<>();

    // lazily initialized (as it requires a context value to first create it)
    private final AtomicReference<SecurityHandler> combinedHandler = new AtomicReference<>();

    private SecurityHandler(Builder builder) {
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

        queryParamHandlers.addAll(builder.queryParamHandlers);

        config.ifPresent(conf -> conf.asNodeList().forEach(node -> configMap.put(node.name(), node)));
    }

    /**
     * Create an instance from configuration.
     * <p>
     * The config expected (example in HOCON format):
     * <pre>
     * {
     *   #
     *   # these are used by {@link WebSecurity} when loaded from config, to register with {@link io.helidon.webserver.WebServer}
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
    static SecurityHandler from(Config config, SecurityHandler defaults) {
        Builder builder = builder(defaults);

        OptionalHelper.from(config.get(KEY_ROLES_ALLOWED).asOptionalList(String.class))
                .ifPresentOrElse(builder::rolesAllowed,
                                 () -> defaults.rolesAllowed.ifPresent(builder::rolesAllowed));
        if (config.exists()) {
            builder.config(config);
        }
        OptionalHelper.from(config.get(KEY_AUTHENTICATOR).value()).or(() -> defaults.explicitAuthenticator).asOptional()
                .ifPresent(builder::authenticator);
        OptionalHelper.from(config.get(KEY_AUTHORIZER).value()).or(() -> defaults.explicitAuthorizer).asOptional()
                .ifPresent(builder::authorizer);
        OptionalHelper.from(config.get(KEY_AUTHENTICATE).asOptional(Boolean.class)).or(() -> defaults.authenticate).asOptional()
                .ifPresent(builder::authenticate);
        OptionalHelper.from(config.get(KEY_AUTHENTICATION_OPTIONAL).asOptional(Boolean.class))
                .or(() -> defaults.authenticationOptional)
                .asOptional().ifPresent(builder::authenticationOptional);
        OptionalHelper.from(config.get(KEY_AUDIT).asOptional(Boolean.class)).or(() -> defaults.audited).asOptional()
                .ifPresent(builder::audit);
        OptionalHelper.from(config.get(KEY_AUTHORIZE).asOptional(Boolean.class)).or(() -> defaults.authorize).asOptional()
                .ifPresent(builder::authorize);
        OptionalHelper.from(config.get(KEY_AUDIT_EVENT_TYPE).value()).or(() -> defaults.auditEventType).asOptional()
                .ifPresent(builder::auditEventType);
        OptionalHelper.from(config.get(KEY_AUDIT_MESSAGE_FORMAT).value()).or(() -> defaults.auditMessageFormat).asOptional()
                .ifPresent(builder::auditMessageFormat);
        config.get(KEY_QUERY_PARAM_HANDLERS).asOptionalList(QueryParamHandler.class)
                .ifPresent(it -> it.forEach(builder::addQueryParamHandler));

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
        config.get(KEY_AUTHENTICATION_OPTIONAL).asOptional(Boolean.class).ifPresent(aBoolean -> {
            if (aBoolean) {
                if (!config.get(KEY_AUTHENTICATE).exists()) {
                    builder.authenticate(true);
                }
            }
        });

        // explicit atn provider implies atn
        config.get(KEY_AUTHENTICATOR).value().ifPresent(value -> {
            if (!config.get(KEY_AUTHENTICATE).exists()) {
                builder.authenticate(true);
            }
        });

        // explicit atz provider implies atz
        config.get(KEY_AUTHORIZER).value().ifPresent(value -> {
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
        OptionalHelper.from(config.get(key).asOptional(clazz)).or(() -> defaultValue).asOptional().ifPresent(builderMethod);
    }

    static SecurityHandler newInstance() {
        // constant is OK, object is immutable
        return DEFAULT_INSTANCE;
    }

    private static Builder builder() {
        return new Builder();
    }

    private static Builder builder(SecurityHandler toCopy) {
        return new Builder().configureFrom(toCopy);
    }

    static void traceError(Span span, Throwable throwable) {
        // failed

        Tags.ERROR.set(span, true);
        span.log(CollectionsHelper.mapOf("event", "error",
                                         "error.object", throwable));

        span.finish();
    }

    void extractQueryParams(SecurityContext securityContext, ServerRequest req) {
        Map<String, List<String>> headers = new HashMap<>();
        queryParamHandlers.forEach(handler -> handler.extract(req, headers));
        //the following line is not possible, as headers are read
        //only in web server, must explicitly send them with security requests
        //headers.forEach(req.headers()::put);

        // update environment in context with the found headers
        securityContext.setEnv(securityContext.getEnv().derive()
                                       .headers(headers)
                                       .build());
    }

    @Override
    public void accept(ServerRequest req, ServerResponse res) {
        //process security
        SecurityContext securityContext = req.context()
                .get(SecurityContext.class)
                .orElseThrow(() -> new SecurityException(
                        "Security context not present. Maybe you forgot to Routing.builder().register(SecurityAdapter.from"
                                + "(security))..."));

        if (combined) {
            processSecurity(securityContext, req, res);
        } else {
            // the following condition may be met for multiple threads - and we don't really care
            // as the result is exactly the same in all cases and doesn't have side effects
            if (null == combinedHandler.get()) {
                // we may have a default handler configured
                SecurityHandler defaultHandler = req.context().get(SecurityHandler.class).orElse(DEFAULT_INSTANCE);

                // intentional same instance comparison, as I want to prevent endless loop
                //noinspection ObjectEquality
                if (defaultHandler == DEFAULT_INSTANCE) {
                    combinedHandler.set(this);
                } else {
                    combinedHandler.compareAndSet(null,
                                                  builder(defaultHandler).configureFrom(this).combined().build());
                }
            }

            combinedHandler.get().processSecurity(securityContext, req, res);
        }

    }

    private void processSecurity(SecurityContext securityContext, ServerRequest req, ServerResponse res) {
        // authentication and authorization
        Tracer tracer = securityContext.getTracer();

        Span securitySpan = tracer
                .buildSpan("security")
                .asChildOf(securityContext.getTracingSpan())
                .start();

        securitySpan.log(CollectionsHelper.mapOf("securityId", securityContext.getId()));

        // extract headers
        extractQueryParams(securityContext, req);

        securityContext.setEndpointConfig(securityContext.getEndpointConfig()
                                                  .derive()
                                                  .configMap(configMap)
                                                  .customObjects(customObjects.orElse(new ClassToInstanceStore<>()))
                                                  .build());

        processAuthentication(req, res, securityContext, tracer, securitySpan)
                .thenCompose(atnResult -> {
                    if (atnResult.proceed) {
                        // authentication was OK or disabled, we should continue
                        return processAuthorization(req, res, securityContext, tracer, securitySpan);
                    } else {
                        // authentication told us to stop processing
                        return CompletableFuture.completedFuture(AtxResult.STOP);
                    }
                })
                .thenAccept(atzResult -> {
                    if (atzResult.proceed) {
                        // authorization was OK, we can continue processing
                        securitySpan.log("status: PROCEED");
                        securitySpan.finish();
                        req.next();
                    } else {
                        securitySpan.log("status: DENY");
                        securitySpan.finish();
                    }
                })
                .exceptionally(throwable -> {
                    traceError(securitySpan, throwable);
                    LOGGER.log(Level.SEVERE, "Unexpected exception during security processing", throwable);
                    abortRequest(res, null, Http.Status.INTERNAL_SERVER_ERROR_500.code(), CollectionsHelper.mapOf());
                    return null;
                });

        // auditing
        res.whenSent().thenAccept(sr -> processAudit(req, sr, securityContext));
    }

    private void processAudit(ServerRequest req, ServerResponse res, SecurityContext securityContext) {
        // make sure we actually should audit
        if (!audited.orElse(true)) {
            // explicitly disabled
            return;
        }

        if (!audited.isPresent()) {
            // use defaults
            if (req.method() instanceof Http.Method) {
                switch ((Http.Method) req.method()) {
                case GET:
                case HEAD:
                    // get and head are not audited by default
                    return;
                case OPTIONS:
                case POST:
                case PUT:
                case DELETE:
                case TRACE:
                default:
                    //do nothing - we want to audit
                }
            }
        }

        //audit
        AuditEvent.AuditSeverity auditSeverity;

        switch (res.status().family()) {
        case INFORMATIONAL:
        case SUCCESSFUL:
        case REDIRECTION:
            auditSeverity = AuditEvent.AuditSeverity.SUCCESS;
            break;
        case CLIENT_ERROR:
        case SERVER_ERROR:
        case OTHER:
        default:
            auditSeverity = AuditEvent.AuditSeverity.FAILURE;
            break;
        }

        SecurityAuditEvent auditEvent = SecurityAuditEvent
                .audit(auditSeverity,
                       auditEventType.orElse(DEFAULT_AUDIT_EVENT_TYPE),
                       auditMessageFormat.orElse(DEFAULT_AUDIT_MESSAGE_FORMAT))
                .addParam(plain("method", req.method()))
                .addParam(plain("path", req.path()))
                .addParam(plain("status", String.valueOf(res.status().code())))
                .addParam(plain("subject", securityContext.getUser().orElse(SecurityContext.ANONYMOUS)))
                .addParam(plain("transport", "http"))
                .addParam(plain("resourceType", "http"))
                .addParam(plain("targetUri", req.uri()));

        securityContext.getService().ifPresent(svc -> auditEvent.addParam(plain("service", svc.toString())));

        securityContext.audit(auditEvent);
    }

    private CompletionStage<AtxResult> processAuthentication(ServerRequest req,
                                                             ServerResponse res,
                                                             SecurityContext securityContext,
                                                             Tracer tracer,
                                                             Span securitySpan) {
        if (!authenticate.orElse(false)) {
            return CompletableFuture.completedFuture(AtxResult.PROCEED);
        }

        CompletableFuture<AtxResult> future = new CompletableFuture<>();

        Span atnSpan = tracer
                .buildSpan("security:atn")
                .asChildOf(securitySpan)
                .start();

        SecurityClientBuilder<AuthenticationResponse> clientBuilder = securityContext.atnClientBuilder();
        configureSecurityRequest(clientBuilder, req, res, atnSpan);

        clientBuilder.explicitProvider(explicitAuthenticator.orElse(null)).submit().thenAccept(response -> {
            switch (response.getStatus()) {
            case SUCCESS:
                //everything is fine, we can continue with processing
                break;
            case FAILURE_FINISH:
                if (atnFinishFailure(res, future, response)) {
                    atnSpanFinish(atnSpan, response);
                    return;
                }
                break;
            case SUCCESS_FINISH:
                atnFinish(res, future, response);
                atnSpanFinish(atnSpan, response);
                return;
            case ABSTAIN:
            case FAILURE:
                if (atnAbstainFailure(res, future, response)) {
                    atnSpanFinish(atnSpan, response);
                    return;
                }
                break;
            default:
                Exception e = new SecurityException("Invalid SecurityStatus returned: " + response.getStatus());
                future.completeExceptionally(e);
                traceError(atnSpan, e);
                return;
            }

            atnSpanFinish(atnSpan, response);
            future.complete(new AtxResult(clientBuilder.buildRequest()));
        }).exceptionally(throwable -> {
            traceError(atnSpan, throwable);
            future.completeExceptionally(throwable);
            return null;
        });

        return future;
    }

    private void atnSpanFinish(Span atnSpan, AuthenticationResponse response) {
        response.getUser()
                .ifPresent(subject -> atnSpan
                        .log("security.user: " + subject.getPrincipal().getName()));

        response.getService()
                .ifPresent(subject -> atnSpan.log("security.service: " + subject.getPrincipal().getName()));

        atnSpan.log("status: " + response.getStatus());

        atnSpan.finish();
    }

    private boolean atnAbstainFailure(ServerResponse res,
                                      CompletableFuture<AtxResult> future,
                                      AuthenticationResponse response) {
        if (authenticationOptional.orElse(false)) {
            LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            return false;
        }

        abortRequest(res,
                     response,
                     Http.Status.UNAUTHORIZED_401.code(),
                     CollectionsHelper.mapOf(Http.Header.WWW_AUTHENTICATE,
                                             CollectionsHelper.listOf("Basic realm=\"Security Realm\"")));
        future.complete(AtxResult.STOP);
        return true;
    }

    private boolean atnFinishFailure(ServerResponse res,
                                     CompletableFuture<AtxResult> future,
                                     AuthenticationResponse response) {

        if (authenticationOptional.orElse(false)) {
            LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            return false;
        } else {
            int defaultStatusCode = Http.Status.UNAUTHORIZED_401.code();

            abortRequest(res, response, defaultStatusCode, CollectionsHelper.mapOf());
            future.complete(AtxResult.STOP);
            return true;
        }
    }

    private void atnFinish(ServerResponse res,
                           CompletableFuture<AtxResult> future,
                           AuthenticationResponse response) {

        int defaultStatusCode = Http.Status.OK_200.code();

        abortRequest(res, response, defaultStatusCode, CollectionsHelper.mapOf());
        future.complete(AtxResult.STOP);
    }

    private void abortRequest(ServerResponse res,
                              SecurityResponse response,
                              int defaultCode,
                              Map<String, List<String>> defaultHeaders) {

        int statusCode = ((null == response) ? defaultCode : response.getStatusCode().orElse(defaultCode));
        Map<String, List<String>> responseHeaders = ((null == response) ? defaultHeaders : response.getResponseHeaders());
        responseHeaders = responseHeaders.isEmpty() ? defaultHeaders : responseHeaders;

        ResponseHeaders httpHeaders = res.headers();

        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            httpHeaders.put(entry.getKey(), entry.getValue());
        }

        res.status(statusCode);
        res.send();
    }

    private void configureSecurityRequest(SecurityRequestBuilder<? extends SecurityRequestBuilder<?>> request,
                                          ServerRequest req,
                                          ServerResponse res,
                                          Span parentSpan) {

        request.optional(authenticationOptional.orElse(false))
                .tracingSpan(parentSpan)
                .requestMessage(toRequestMessage(req))
                .responseMessage(toResponseMessage(res));
    }

    private Entity toResponseMessage(ServerResponse res) {
        return filterFunction -> res.registerFilter(responseChunkPublisher -> {
            Flow.Publisher<ByteBuffer> bytesFromBizLogic =
                    new ResponseProcessor<DataChunk, ByteBuffer>(responseChunkPublisher) {
                        @Override
                        public void onNext(DataChunk item) {
                            // chunk not released, as security provider may use it later
                            getSubscriber().onNext(item.data());
                        }
                    };
            Flow.Publisher<ByteBuffer> securedBytes = filterFunction.apply(bytesFromBizLogic);
            return new ResponseProcessor<ByteBuffer, DataChunk>(securedBytes) {
                @Override
                public void onNext(ByteBuffer item) {
                    getSubscriber().onNext(DataChunk.create(true, item));
                }
            };
        });
    }

    private Entity toRequestMessage(ServerRequest req) {
        return filterFunction -> req.content().registerFilter(requestChunkPublisher -> {
            Flow.Publisher<ByteBuffer> bytesFromExternal =
                    new ResponseProcessor<DataChunk, ByteBuffer>(requestChunkPublisher) {
                        @Override
                        public void onNext(DataChunk item) {
                            // chunk not released, as security provider may use it later
                            getSubscriber().onNext(item.data());
                        }
                    };
            Flow.Publisher<ByteBuffer> securedBytes = filterFunction.apply(bytesFromExternal);
            return new ResponseProcessor<ByteBuffer, DataChunk>(securedBytes) {
                @Override
                public void onNext(ByteBuffer item) {
                    getSubscriber().onNext(DataChunk.create(item));
                }
            };
        });
    }

    @SuppressWarnings("ThrowableNotThrown")
    private CompletionStage<AtxResult> processAuthorization(ServerRequest req,
                                                            ServerResponse res,
                                                            SecurityContext context,
                                                            Tracer tracer,
                                                            Span securitySpan) {
        CompletableFuture<AtxResult> future = new CompletableFuture<>();

        if (!authorize.orElse(false)) {
            future.complete(AtxResult.PROCEED);
            return future;
        }

        Span atzSpan = tracer
                .buildSpan("security:atz")
                .asChildOf(securitySpan)
                .start();

        Set<String> rolesSet = rolesAllowed.orElse(CollectionsHelper.setOf());

        if (!rolesSet.isEmpty()) {
            // first validate roles - RBAC is supported out of the box by security, no need to invoke provider
            if (explicitAuthorizer.isPresent()) {
                if (rolesSet.stream().noneMatch(role -> context.isUserInRole(role, explicitAuthorizer.get()))) {
                    abortRequest(res, null, Http.Status.FORBIDDEN_403.code(), CollectionsHelper.mapOf());
                    future.complete(AtxResult.STOP);
                    atzSpan.finish();
                    return future;
                }
            } else {
                if (rolesSet.stream().noneMatch(context::isUserInRole)) {
                    abortRequest(res, null, Http.Status.FORBIDDEN_403.code(), CollectionsHelper.mapOf());
                    future.complete(AtxResult.STOP);
                    atzSpan.finish();
                    return future;
                }
            }
        }

        SecurityClientBuilder<AuthorizationResponse> client;

        client = context.atzClientBuilder();
        configureSecurityRequest(client, req, res, atzSpan);

        client.explicitProvider(explicitAuthorizer.orElse(null)).submit().thenAccept(response -> {
            switch (response.getStatus()) {
            case SUCCESS:
                //everything is fine, we can continue with processing
                break;
            case FAILURE_FINISH:
            case SUCCESS_FINISH:
                int defaultStatus = (response.getStatus() == AuthenticationResponse.SecurityStatus.FAILURE_FINISH)
                        ? Http.Status.FORBIDDEN_403.code()
                        : Http.Status.OK_200.code();

                atzSpan.finish();
                abortRequest(res, response, defaultStatus, CollectionsHelper.mapOf());
                future.complete(AtxResult.STOP);
                return;
            case ABSTAIN:
            case FAILURE:
                atzSpan.finish();
                abortRequest(res, response, Http.Status.FORBIDDEN_403.code(), CollectionsHelper.mapOf());
                future.complete(AtxResult.STOP);
                return;
            default:
                SecurityException e = new SecurityException("Invalid SecurityStatus returned: " + response.getStatus());
                traceError(atzSpan, e);
                future.completeExceptionally(e);
                return;
            }

            atzSpan.finish();
            // everything was OK
            future.complete(AtxResult.PROCEED);
        }).exceptionally(throwable -> {
            traceError(atzSpan, throwable);
            future.completeExceptionally(throwable);
            return null;
        });

        return future;
    }

    /**
     * List of query parameter handlers.
     *
     * @return list of handlers
     */
    public List<QueryParamHandler> getQueryParamHandlers() {
        return Collections.unmodifiableList(queryParamHandlers);
    }

    /**
     * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
     * Will enable authentication.
     *
     * @param explicitAuthenticator name of authenticator as configured in {@link Security}
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authenticator(String explicitAuthenticator) {
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
    public SecurityHandler authorizer(String explicitAuthorizer) {
        return builder(this).authorizer(explicitAuthorizer).build();
    }

    /**
     * An array of allowed roles for this path - must have a security provider supporting roles (either authentication
     * or authorization provider).
     * This method enables authentication and authorization (you can disable them again by calling
     * {@link SecurityHandler#skipAuthorization()}
     * and {@link #skipAuthentication()} if needed).
     *
     * @param roles if subject is any of these roles, allow access
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler rolesAllowed(String... roles) {
        return builder(this).rolesAllowed(roles).authorize(true).authenticate(true).build();

    }

    /**
     * If called, authentication failure will not abort request and will continue as anonymous (authentication is not optional
     * by default).
     * Will enable authentication.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authenticationOptional() {
        return builder(this).authenticationOptional(true).build();
    }

    /**
     * If called, request will go through authentication process - (authentication is disabled by default - it may be enabled
     * as a side effect of other methods, such as {@link #rolesAllowed(String...)}.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authenticate() {
        return builder(this).authenticate(true).build();
    }

    /**
     * If called, request will NOT go through authentication process. Use this when another method implies authentication
     * (such as {@link #rolesAllowed(String...)}) and yet it is not desired (e.g. everything is handled by authorization).
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler skipAuthentication() {
        return builder(this).authenticate(false).build();
    }

    /**
     * Register a custom object for security request(s).
     * This creates a hard dependency on a specific security provider, so use with care.
     *
     * @param object An object expected by security provider
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler customObject(Object object) {
        return builder(this).customObject(object).build();
    }

    /**
     * Override for event-type, defaults to {@value #DEFAULT_AUDIT_EVENT_TYPE}.
     *
     * @param eventType audit event type to use
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler auditEventType(String eventType) {
        return builder(this).auditEventType(eventType).build();
    }

    /**
     * Override for audit message format, defaults to {@value #DEFAULT_AUDIT_MESSAGE_FORMAT}.
     *
     * @param messageFormat audit message format to use
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler auditMessageFormat(String messageFormat) {
        return builder(this).auditMessageFormat(messageFormat).build();
    }

    /**
     * If called, request will go through authorization process - (authorization is disabled by default - it may be enabled
     * as a side effect of other methods, such as {@link #rolesAllowed(String...)}.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authorize() {
        return builder(this).authorize(true).build();
    }

    /**
     * Skip authorization for this route.
     * Use this when authorization is implied by another method on this class (e.g. {@link #rolesAllowed(String...)} and
     * you want to explicitly forbid it.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler skipAuthorization() {
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
    public SecurityHandler audit() {
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
    public SecurityHandler skipAudit() {
        return builder(this).audit(false).build();
    }

    /**
     * Add a query parameter extraction configuration.
     *
     * @param queryParamName name of a query parameter to extract
     * @param headerHandler  handler to extract it and store it in a header field
     * @return new handler instance
     */
    public SecurityHandler queryParam(String queryParamName, TokenHandler headerHandler) {
        return builder(this)
                .addQueryParamHandler(QueryParamHandler.from(queryParamName, headerHandler))
                .build();
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

    private abstract static class ResponseProcessor<T, R> implements Flow.Processor<T, R> {
        private final CountDownLatch subscribedLatch = new CountDownLatch(1);
        private final Flow.Publisher<T> externalPublisher;
        private final AtomicBoolean isSubscribed = new AtomicBoolean();
        private Flow.Subscriber<? super R> subscriber;
        private volatile Flow.Subscription mySubscription;
        private volatile Flow.Subscription subscribersSubscription;

        ResponseProcessor(Flow.Publisher<T> externalPublisher) {
            this.externalPublisher = externalPublisher;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super R> subscriber) {
            if (!isSubscribed.compareAndSet(false, true)) {
                subscriber.onError(new IllegalStateException("This publisher only supports a single subscriber."));
                return;
            }
            this.subscriber = subscriber;
            //somebody subscribed to us!
            this.subscribersSubscription = new Flow.Subscription() {
                @Override
                public void request(long n) {
                    try {
                        subscribedLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        mySubscription.cancel();
                        return;
                    }
                    mySubscription.request(n);
                }

                @Override
                public void cancel() {
                    try {
                        subscribedLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    mySubscription.cancel();
                }
            };
            externalPublisher.subscribe(this);
            subscriber.onSubscribe(subscribersSubscription);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.mySubscription = subscription;
            subscribedLatch.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        Flow.Subscriber<? super R> getSubscriber() {
            return subscriber;
        }
    }

    // WARNING: builder methods must not have side-effects, as they are used to build instance from configuration
    // if you want side effects, use methods on SecurityHandler
    private static final class Builder implements io.helidon.common.Builder<SecurityHandler> {
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
        private final List<QueryParamHandler> queryParamHandlers = new LinkedList<>();
        private boolean combined;

        private Builder() {
        }

        @Override
        public SecurityHandler build() {
            return new SecurityHandler(this);
        }

        private Builder combined() {
            this.combined = true;

            return this;
        }

        // add to this builder
        private Builder configureFrom(SecurityHandler handler) {
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
            this.queryParamHandlers.addAll(handler.getQueryParamHandlers());

            return this;
        }

        private Builder customObjects(ClassToInstanceStore<Object> store) {
            OptionalHelper.from(customObjects)
                    .ifPresentOrElse(myStore -> myStore.putAll(store), () -> {
                        ClassToInstanceStore<Object> ctis = new ClassToInstanceStore<>();
                        ctis.putAll(store);
                        this.customObjects = Optional.of(ctis);
                    });

            return this;
        }

        /**
         * Add a new handler to extract query parameter and store it in security request header.
         *
         * @param handler handler to extract data
         * @return updated builder instance
         */
        public Builder addQueryParamHandler(QueryParamHandler handler) {
            this.queryParamHandlers.add(handler);
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
            OptionalHelper.from(customObjects)
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
            OptionalHelper.from(rolesAllowed).ifPresentOrElse(strings -> strings.addAll(roles),
                                                              () -> {
                                                                  Set<String> newRoles = new HashSet<>(roles);
                                                                  rolesAllowed = Optional.of(newRoles);
                                                              });
            return this;
        }
    }

    /**
     * Handler of query parameters - extracts them and stores
     * them in a security header, so security can access them.
     */
    public static final class QueryParamHandler {
        private final String queryParamName;
        private final TokenHandler headerHandler;

        private QueryParamHandler(QueryParamMapping mapping) {
            this.queryParamName = mapping.getQueryParamName();
            this.headerHandler = mapping.getTokenHandler();
        }

        /**
         * Create an instance from configuration.
         *
         * @param config configuration instance
         * @return new instance of query parameter handler
         */
        public static QueryParamHandler from(Config config) {
            return from(QueryParamMapping.from(config));
        }

        /**
         * Create an instance from existing mapping.
         *
         * @param mapping existing mapping
         * @return new instance of query parameter handler
         */
        public static QueryParamHandler from(QueryParamMapping mapping) {
            return new QueryParamHandler(mapping);
        }

        /**
         * Create an instance from parameter name and explicit {@link TokenHandler}.
         *
         * @param queryParamName name of parameter
         * @param headerHandler  handler to extract parameter and store the header
         * @return new instance of query parameter handler
         */
        public static QueryParamHandler from(String queryParamName, TokenHandler headerHandler) {
            return from(QueryParamMapping.create(queryParamName, headerHandler));
        }

        void extract(ServerRequest req, Map<String, List<String>> headers) {
            List<String> values = req.queryParams().all(queryParamName);

            values.forEach(token -> {
                               String tokenValue = headerHandler.extractToken(token);
                               headerHandler.addHeader(headers, tokenValue);
                           }
            );
        }
    }
}

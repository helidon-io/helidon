/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.security.AuditEvent;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ClassToInstanceStore;
import io.helidon.security.QueryParamMapping;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityRequest;
import io.helidon.security.SecurityRequestBuilder;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.AtnTracing;
import io.helidon.security.integration.common.AtzTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.util.TokenHandler;
import io.helidon.tracing.SpanContext;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.security.AuditEvent.AuditParam.plain;

/**
 * Handles security for web server. This handler is registered either by hand on router config,
 * or automatically from configuration when integration done through {@link io.helidon.webserver.security.SecurityFeature},
 * or {@link SecurityHttpFeature#create(io.helidon.common.config.Config)}.
 */
// we need to have all fields optional and this is cleaner than checking for null
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@RuntimeType.PrototypedBy(SecurityHandlerConfig.class)
public final class SecurityHandler implements Handler, RuntimeType.Api<SecurityHandlerConfig> {
    static final String DEFAULT_AUDIT_EVENT_TYPE = "request";
    static final String DEFAULT_AUDIT_MESSAGE_FORMAT = "%3$s %1$s \"%2$s\" %5$s %6$s requested by %4$s";
    private static final System.Logger LOGGER = System.getLogger(SecurityHandler.class.getName());
    private static final SecurityHandler DEFAULT_INSTANCE = builder().build();

    private final SecurityHandlerConfig config;
    private final Optional<Set<String>> rolesAllowed;
    private final Optional<ClassToInstanceStore<Object>> customObjects;
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

    private SecurityHandler(SecurityHandlerConfig config) {
        this.config = config;

        // must copy values to be safely immutable
        Set<String> rolesAllowedSet = config.rolesAllowed();
        if (rolesAllowedSet.isEmpty()) {
            this.rolesAllowed = Optional.empty();
        } else {
            this.rolesAllowed = Optional.of(rolesAllowedSet);
        }

        // must copy values to be safely immutable
        this.customObjects = config.customObjects()
                .map(it -> {
                    ClassToInstanceStore<Object> ctis = new ClassToInstanceStore<>();
                    ctis.putAll(it);
                    return ctis;
                });

        explicitAuthenticator = config.authenticator();
        explicitAuthorizer = config.authorizer();
        authenticate = config.authenticate();
        authenticationOptional = config.authenticationOptional();
        audited = config.audit();
        auditEventType = config.auditEventType();
        auditMessageFormat = config.auditMessageFormat();
        authorize = config.authorize();
        combined = config.combined();

        queryParamHandlers.addAll(config.queryParams());

        config.config().ifPresent(conf -> conf.asNodeList().get().forEach(node -> configMap.put(node.name(), node)));
    }

    /**
     * Create a new fluent API builder for security handler.
     *
     * @return a new builder
     */
    public static SecurityHandlerConfig.Builder builder() {
        return SecurityHandlerConfig.builder();
    }

    /**
     * Create a new instance, customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new configured handler
     */
    public static SecurityHandler create(Consumer<SecurityHandlerConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create an instance from configuration.
     * <p>
     * The config expected (example in HOCON format):
     * <pre>
     * {
     *   #
     *   # these are used by {@link SecurityHttpFeature} when loaded from config, to register with {@link io.helidon.webserver.WebServer}
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
    public static SecurityHandler create(Config config, SecurityHandler defaults) {
        return builder()
                .from(defaults.prototype())
                .config(config)
                .build();
    }

    static SecurityHandler create(SecurityHandlerConfig config) {
        return new SecurityHandler(config);
    }

    static SecurityHandler create() {
        // constant is OK, object is immutable
        return DEFAULT_INSTANCE;
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) {
        Context context = Contexts.context()
                .orElseGet(req::context);
        //process security
        SecurityContext securityContext = context
                .get(SecurityContext.class)
                .orElseThrow(() -> new SecurityException(
                        "Security context not present. The security feature must be applied on this socket."));

        if (combined) {
            processSecurity(securityContext, req, res);
        } else {
            // the following condition may be met for multiple threads - and we don't really care
            // as the result is exactly the same in all cases and doesn't have side effects
            if (null == combinedHandler.get()) {
                // we may have a default handler configured
                SecurityHandler defaultHandler = context.get(SecurityHandler.class).orElse(DEFAULT_INSTANCE);

                // intentional same instance comparison, as I want to prevent endless loop
                //noinspection ObjectEquality
                if (defaultHandler == DEFAULT_INSTANCE) {
                    combinedHandler.set(this);
                } else {
                    combinedHandler.compareAndSet(null,
                                                  builder().from(defaultHandler.prototype())
                                                          .from(this.prototype())
                                                          .combined(true)
                                                          .build());
                }
            }

            combinedHandler.get().processSecurity(securityContext, req, res);
        }

    }

    @Override
    public SecurityHandlerConfig prototype() {
        return config;
    }

    /**
     * List of query parameter handlers.
     *
     * @return list of handlers
     */
    public List<QueryParamHandler> queryParamHandlers() {
        return Collections.unmodifiableList(queryParamHandlers);
    }

    /**
     * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
     * Will enable authentication.
     *
     * @param explicitAuthenticator name of authenticator as configured in {@link io.helidon.security.Security}
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authenticator(String explicitAuthenticator) {
        return builder().from(prototype()).authenticator(explicitAuthenticator).build();
    }

    /**
     * Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
     * permitted).
     * Will enable authorization.
     *
     * @param explicitAuthorizer name of authorizer as configured in {@link io.helidon.security.Security}
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authorizer(String explicitAuthorizer) {
        return builder().from(prototype()).authorizer(explicitAuthorizer).build();
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
        return builder().from(prototype()).rolesAllowed(Set.of(roles)).authorize(true).authenticate(true).build();

    }

    /**
     * If called, authentication failure will not abort request and will continue as anonymous (authentication is not optional
     * by default).
     * Will enable authentication.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authenticationOptional() {
        return builder().from(prototype()).authenticationOptional(true).build();
    }

    /**
     * If called, request will go through authentication process - (authentication is disabled by default - it may be enabled
     * as a side effect of other methods, such as {@link #rolesAllowed(String...)}.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authenticate() {
        return builder().from(prototype()).authenticate(true).build();
    }

    /**
     * If called, request will NOT go through authentication process. Use this when another method implies authentication
     * (such as {@link #rolesAllowed(String...)}) and yet it is not desired (e.g. everything is handled by authorization).
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler skipAuthentication() {
        return builder().from(prototype()).authenticate(false).build();
    }

    /**
     * Register a custom object for security request(s).
     * This creates a hard dependency on a specific security provider, so use with care.
     *
     * @param object An object expected by security provider
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler customObject(Object object) {
        return builder().from(prototype()).addObject(object).build();
    }

    /**
     * Override for event-type, defaults to {@value #DEFAULT_AUDIT_EVENT_TYPE}.
     *
     * @param eventType audit event type to use
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler auditEventType(String eventType) {
        return builder().from(prototype()).auditEventType(eventType).build();
    }

    /**
     * Override for audit message format, defaults to {@value #DEFAULT_AUDIT_MESSAGE_FORMAT}.
     *
     * @param messageFormat audit message format to use
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler auditMessageFormat(String messageFormat) {
        return builder().from(prototype()).auditMessageFormat(messageFormat).build();
    }

    /**
     * If called, request will go through authorization process - (authorization is disabled by default - it may be enabled
     * as a side effect of other methods, such as {@link #rolesAllowed(String...)}.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler authorize() {
        return builder().from(prototype()).authorize(true).build();
    }

    /**
     * Skip authorization for this route.
     * Use this when authorization is implied by another method on this class (e.g. {@link #rolesAllowed(String...)} and
     * you want to explicitly forbid it.
     *
     * @return new handler instance with configuration of this instance updated with this method
     */
    public SecurityHandler skipAuthorization() {
        return builder().from(prototype()).authorize(false).build();
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
        return builder().from(prototype()).audit(true).build();
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
        return builder().from(prototype()).audit(false).build();
    }

    /**
     * Add a query parameter extraction configuration.
     *
     * @param queryParamName name of a query parameter to extract
     * @param headerHandler  handler to extract it and store it in a header field
     * @return new handler instance
     */
    public SecurityHandler queryParam(String queryParamName, TokenHandler headerHandler) {
        return builder().from(prototype())
                .addQueryParam(QueryParamHandler.create(queryParamName, headerHandler))
                .build();
    }

    void extractQueryParams(SecurityContext securityContext, ServerRequest req) {
        Map<String, List<String>> headers = new HashMap<>();
        queryParamHandlers.forEach(handler -> handler.extract(req, headers));
        //the following line is not possible, as headers are read
        //only in web server, must explicitly send them with security requests
        //headers.forEach(req.headers()::put);

        // update environment in context with the found headers
        securityContext.env(securityContext.env().derive()
                                    .headers(headers)
                                    .build());
    }

    private static <T> void configure(Config config,
                                      String key,
                                      Optional<T> defaultValue,
                                      Consumer<T> builderMethod,
                                      Class<T> clazz) {
        config.get(key).as(clazz).or(() -> defaultValue).ifPresent(builderMethod);
    }

    private void processSecurity(SecurityContext securityContext, ServerRequest req, ServerResponse res) {
        // authentication and authorization

        // start security span
        SecurityTracing tracing = SecurityTracing.get();
        tracing.securityContext(securityContext);

        // extract headers
        extractQueryParams(securityContext, req);

        securityContext.endpointConfig(securityContext.endpointConfig()
                                               .derive()
                                               .configMap(configMap)
                                               .customObjects(customObjects.orElse(new ClassToInstanceStore<>()))
                                               .build());

        try {
            AtxResult atnResult = processAuthentication(res, securityContext, tracing.atnTracing());

            AtxResult atzResult;
            if (atnResult.proceed) {
                atzResult = processAuthorization(req, res, securityContext, tracing.atzTracing());
            } else {
                atzResult = AtxResult.STOP;
            }

            if (atzResult.proceed) {
                // authorization was OK, we can continue processing
                tracing.logProceed();
                tracing.finish();

                // propagate context information in call to next
                res.next();
            } else {
                tracing.logDeny();
                tracing.finish();
            }
        } catch (Exception e) {
            tracing.error(e);
            LOGGER.log(System.Logger.Level.ERROR, "Unexpected exception during security processing", e);
            abortRequest(res, null, Status.INTERNAL_SERVER_ERROR_500.code(), Map.of());
        }

        // auditing
        res.whenSent(() -> processAudit(req, res, securityContext));
    }

    private void processAudit(ServerRequest req, ServerResponse res, SecurityContext securityContext) {

        Method method = req.prologue().method();

        // make sure we actually should audit
        if (!audited.orElse(true)) {
            // explicitly disabled
            return;
        }

        if (audited.isEmpty()) {
            // use defaults
            if (method == Method.GET || method == Method.HEAD) {
                // get and head are not audited by default
                return;
            }
            //do nothing - we want to audit
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
                .addParam(plain("method", method))
                .addParam(plain("path", req.path()))
                .addParam(plain("status", String.valueOf(res.status().code())))
                .addParam(plain("subject", securityContext.user().orElse(SecurityContext.ANONYMOUS)))
                .addParam(plain("transport", "http"))
                .addParam(plain("resourceType", "http"))
                .addParam(plain("targetUri", req.prologue().uriPath().rawPath()));

        securityContext.service().ifPresent(svc -> auditEvent.addParam(plain("service", svc.toString())));

        securityContext.audit(auditEvent);
    }

    private AtxResult processAuthentication(ServerResponse res,
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
            // copy headers to be returned with the current response
            response.responseHeaders()
                    .forEach((key, value) -> res.headers().set(HeaderValues.create(key, value)));

            switch (response.status()) {
            case SUCCESS:
                //everything is fine, we can continue with processing
                break;
            case FAILURE_FINISH:
                if (atnFinishFailure(res, response)) {
                    atnSpanFinish(atnTracing, response);
                    return AtxResult.STOP;
                }
                break;
            case SUCCESS_FINISH:
                atnFinish(res, response);
                atnSpanFinish(atnTracing, response);
                return AtxResult.STOP;
            case ABSTAIN:
            case FAILURE:
                if (atnAbstainFailure(res, response)) {
                    atnSpanFinish(atnTracing, response);
                    return AtxResult.STOP;
                }
                break;
            default:
                throw new SecurityException("Invalid SecurityStatus returned: " + response.status());
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

    private boolean atnAbstainFailure(ServerResponse res, AuthenticationResponse response) {
        if (authenticationOptional.orElse(false)) {
            LOGGER.log(System.Logger.Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            return false;
        }

        abortRequest(res,
                     response,
                     Status.UNAUTHORIZED_401.code(),
                     Map.of());
        return true;
    }

    private boolean atnFinishFailure(ServerResponse res, AuthenticationResponse response) {
        if (authenticationOptional.orElse(false)) {
            LOGGER.log(System.Logger.Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            return false;
        } else {
            int defaultStatusCode = Status.UNAUTHORIZED_401.code();

            abortRequest(res, response, defaultStatusCode, Map.of());
            return true;
        }
    }

    private void atnFinish(ServerResponse res, AuthenticationResponse response) {
        int defaultStatusCode = Status.OK_200.code();
        abortRequest(res, response, defaultStatusCode, Map.of());
    }

    private void abortRequest(ServerResponse res,
                              SecurityResponse response,
                              int defaultCode,
                              Map<HeaderName, List<String>> defaultHeaders) {

        int statusCode = ((null == response) ? defaultCode : response.statusCode().orElse(defaultCode));
        Map<HeaderName, List<String>> responseHeaders;
        if (response == null) {
            responseHeaders = defaultHeaders;
        } else {
            Map<HeaderName, List<String>> tmpHeaders = new HashMap<>();
            response.responseHeaders()
                    .forEach((key, value) -> tmpHeaders.put(HeaderNames.create(key), value));
            responseHeaders = tmpHeaders;
        }

        responseHeaders = responseHeaders.isEmpty() ? defaultHeaders : responseHeaders;

        ServerResponseHeaders httpHeaders = res.headers();

        for (Map.Entry<HeaderName, List<String>> entry : responseHeaders.entrySet()) {
            httpHeaders.set(entry.getKey(), entry.getValue());
        }

        res.status(Status.create(statusCode));
        res.send();
    }

    private void configureSecurityRequest(SecurityRequestBuilder<? extends SecurityRequestBuilder<?>> request,
                                          SpanContext parentSpanContext) {

        request.optional(authenticationOptional.orElse(false))
                .tracingSpan(parentSpanContext);
    }

    private AtxResult processAuthorization(ServerRequest req,
                                           ServerResponse res,
                                           SecurityContext context,
                                           AtzTracing atzTracing) {

        if (!authorize.orElse(false)) {
            atzTracing.logStatus(SecurityResponse.SecurityStatus.ABSTAIN);
            atzTracing.finish();
            return AtxResult.PROCEED;
        }

        Set<String> rolesSet = rolesAllowed.orElse(Set.of());

        if (!rolesSet.isEmpty()) {
            /*
            As this part bypasses authorization providers, audit logging is not done, we need to explicitly audit this!
             */
            // first validate roles - RBAC is supported out of the box by security, no need to invoke provider
            if (explicitAuthorizer.isPresent()) {
                if (rolesSet.stream().noneMatch(role -> context.isUserInRole(role, explicitAuthorizer.get()))) {
                    auditRoleMissing(context, req.path(), context.user(), rolesSet);
                    abortRequest(res, null, Status.FORBIDDEN_403.code(), Map.of());
                    atzTracing.finish();
                    return AtxResult.STOP;
                }
            } else {
                if (rolesSet.stream().noneMatch(context::isUserInRole)) {
                    auditRoleMissing(context, req.path(), context.user(), rolesSet);
                    abortRequest(res, null, Status.FORBIDDEN_403.code(), Map.of());
                    atzTracing.finish();
                    return AtxResult.STOP;
                }
            }
        }

        SecurityClientBuilder<AuthorizationResponse> client;

        client = context.atzClientBuilder();
        configureSecurityRequest(client,
                                 atzTracing.findParent().orElse(null));

        try {
            AuthorizationResponse response = client.explicitProvider(explicitAuthorizer.orElse(null)).submit();
            atzTracing.logStatus(response.status());

            switch (response.status()) {
            case SUCCESS:
                //everything is fine, we can continue with processing
                break;
            case FAILURE_FINISH:
            case SUCCESS_FINISH:
                int defaultStatus = (response.status() == AuthenticationResponse.SecurityStatus.FAILURE_FINISH)
                        ? Status.FORBIDDEN_403.code()
                        : Status.OK_200.code();

                atzTracing.finish();
                abortRequest(res, response, defaultStatus, Map.of());
                return AtxResult.STOP;
            case ABSTAIN:
            case FAILURE:
                atzTracing.finish();
                abortRequest(res, response, Status.FORBIDDEN_403.code(), Map.of());
                return AtxResult.STOP;
            default:
                throw new SecurityException("Invalid SecurityStatus returned: " + response.status());
            }

            atzTracing.finish();
            // everything was OK
            return AtxResult.PROCEED;
        } catch (Exception e) {
            atzTracing.error(e);
            throw e;
        }
    }

    private void auditRoleMissing(SecurityContext context,
                                  RoutedPath path,
                                  Optional<Subject> user,
                                  Set<String> rolesSet) {

        context.audit(SecurityAuditEvent.failure(AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                                                 "User is not in any of the required roles: %s. Path %s. Subject %s")
                              .addParam(AuditEvent.AuditParam.plain("roles", rolesSet))
                              .addParam(AuditEvent.AuditParam.plain("path", path))
                              .addParam(AuditEvent.AuditParam.plain("subject", user)));
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
     * Handler of query parameters - extracts them and stores
     * them in a security header, so security can access them.
     */
    public static final class QueryParamHandler {
        private final String queryParamName;
        private final TokenHandler headerHandler;

        private QueryParamHandler(QueryParamMapping mapping) {
            this.queryParamName = mapping.queryParamName();
            this.headerHandler = mapping.tokenHandler();
        }

        /**
         * Create an instance from configuration.
         *
         * @param config configuration instance
         * @return new instance of query parameter handler
         */
        public static QueryParamHandler create(Config config) {
            return create(QueryParamMapping.create(config));
        }

        /**
         * Create an instance from existing mapping.
         *
         * @param mapping existing mapping
         * @return new instance of query parameter handler
         */
        public static QueryParamHandler create(QueryParamMapping mapping) {
            return new QueryParamHandler(mapping);
        }

        /**
         * Create an instance from parameter name and explicit {@link TokenHandler}.
         *
         * @param queryParamName name of parameter
         * @param headerHandler  handler to extract parameter and store the header
         * @return new instance of query parameter handler
         */
        public static QueryParamHandler create(String queryParamName, TokenHandler headerHandler) {
            return create(QueryParamMapping.create(queryParamName, headerHandler));
        }

        void extract(ServerRequest req, Map<String, List<String>> headers) {
            UriQuery uriQuery = req.query();
            if (uriQuery.contains(queryParamName)) {
                List<String> values = uriQuery.all(queryParamName);

                values.forEach(token -> {
                                   String tokenValue = headerHandler.extractToken(token);
                                   headerHandler.addHeader(headers, tokenValue);
                               }
                );
            }
        }
    }
}

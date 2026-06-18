/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.config.Config;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.security.AuditEvent;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityClientBuilder;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityRequestBuilder;
import io.helidon.security.SecurityResponse;
import io.helidon.security.SecurityTime;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.AtnTracing;
import io.helidon.security.integration.common.AtzTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Service.Singleton
@Weight(800)
class HttpSecurityInterceptor implements Interception.EntryPointInterceptor {
    private static final System.Logger LOGGER = System.getLogger(HttpSecurityInterceptor.class.getName());
    private static final TypeName AUTHENTICATED_TYPE = TypeName.create("io.helidon.security.annotations.Authenticated");
    private static final TypeName AUTHORIZED_TYPE = TypeName.create("io.helidon.security.annotations.Authorized");
    private static final TypeName AUDITED_TYPE = TypeName.create("io.helidon.security.annotations.Audited");

    private final Security security;
    private final Config config;
    private final List<AnnotationAnalyzer> annotationAnalyzers;
    private final List<SecurityResponseMapper> responseMappers;

    private final Map<TypeName, HttpSecurityDefinition> typeSecurityDefinitions = new HashMap<>();
    private final ReentrantReadWriteLock typeSecurityDefinitionLock = new ReentrantReadWriteLock();
    private final Map<Signature, HttpSecurityDefinition> methodSecurityDefinitions = new HashMap<>();
    private final ReentrantReadWriteLock methodSecurityDefinitionLock = new ReentrantReadWriteLock();

    HttpSecurityInterceptor(Security security,
                            Config config,
                            List<AnnotationAnalyzer> annotationAnalyzers,
                            List<SecurityResponseMapper> responseMappers) {
        this.security = security;
        this.config = config.get("server.features.security.declarative");
        this.annotationAnalyzers = annotationAnalyzers;
        this.responseMappers = responseMappers;
    }

    @Override
    public <T> T proceed(InterceptionContext ctx,
                         Interception.Interceptor.Chain<T> chain,
                         Object... args) throws Exception {
        if (args.length == 2 && args[0] instanceof ServerRequest req && args[1] instanceof ServerResponse res) {
            return proceedHttp(ctx, chain, args, req, res);
        }

        return proceedGeneric(ctx, chain, args);
    }

    private <T> T proceedHttp(InterceptionContext ctx,
                              Interception.Interceptor.Chain<T> chain,
                              Object[] args,
                              ServerRequest req,
                              ServerResponse res) throws Exception {
        HttpSecurityDefinition definition = methodSecurity(ctx);

        if (definition.noSecurity()) {
            return chain.proceed(args);
        }

        /*
        Now whatever security was supposed to be handled by WebServer security is done
            (based on Security feature, usually from configuration)
        This code is responsible for
        - analyzing the annotations on the type and method
        - making sure that all was done, and if not, it does it here
        - we may want to code generate the security handler for each method, and just look it up based on name
            (such `as io.helidon.examples.declarative.GreetEndpoint.greet(java.lang.String)`)
        - for now, we create the handler in memory and cache it, and then invoke it
         */

        SecurityTracing tracing = SecurityTracing.get(req.context());
        UriInfo requestedUri = req.requestedUri();
        var prologue = req.prologue();
        SecurityContext securityContext = req.context()
                .get(SecurityContext.class)
                .orElseThrow(() -> new IllegalStateException("No security context in request context. "
                                                                     + "Make sure the ContextFeature is added to WebServer"));
        SecurityEnvironment currentEnvironment = securityContext.env();

        String resourceType = ctx.serviceInfo().serviceType().fqName();
        var securityEnvironment = SecurityEnvironment.builder(security.serverTime())
                .transport(requestedUri.scheme())
                .path(requestedUri.path().path())
                .targetUri(requestedUri.toUri())
                .method(prologue.method().text())
                .requestedMethod(currentEnvironment.requestedMethod())
                .requestedPath(currentEnvironment.requestedPath())
                .requestedQuery(currentEnvironment.requestedQuery())
                .queryParams(req.query())
                .headers(SecurityContextFilter.headers(req))
                .addAttribute("resourceType", resourceType)
                .addAttribute("userIp", req.remotePeer().host())
                .addAttribute("userPort", req.remotePeer().port())
                .build();

        var ec = EndpointConfig.builder()
                .securityLevels(definition.securityLevels())
                .build();

        var ictx = new HttpSecurityInterceptorContext();

        try {
            securityContext.env(securityEnvironment);
            securityContext.endpointConfig(ec);

            processSecurity(req, res, ictx, tracing, definition, securityContext);
        } finally {
            if (ictx.traceSuccess()) {
                tracing.logProceed();
                tracing.finish();
            } else {
                tracing.logDeny();
                tracing.error("aborted");
            }
        }

        if (ictx.shouldFinish()) {
            return null;
        }

        T result = chain.proceed(args);
        if (definition.atzExplicit() && !securityContext.isAuthorized()) {
            if (res.status().family() == Status.Family.CLIENT_ERROR
                    || res.status().family() == Status.Family.SERVER_ERROR) {
                // failure returned anyway - may have never reached the endpoint
                return result;
            }
            if (res.isSent()) {
                LOGGER.log(Level.ERROR, "Authorization failure. Request for"
                        + req.prologue().uriPath().absolute().path()
                        + " has failed, it was marked for explicit authorization, "
                        + "yet authorization was never called on security context. The "
                        + "method was invoked and may have changed data, and it has sent a response."
                        + " Endpoint: " + ctx.serviceInfo().serviceType().fqName() + ", method: "
                        + ctx.elementInfo().elementName());
            } else {
                LOGGER.log(Level.ERROR, "Authorization failure. Request for"
                        + req.prologue().uriPath().absolute().path()
                        + " has failed, it was marked for explicit authorization, "
                        + "yet authorization was never called on security context. The "
                        + "method was invoked and may have changed data. Marking as internal server error."
                        + " Endpoint: " + ctx.serviceInfo().serviceType().fqName() + ", method: "
                        + ctx.elementInfo().elementName());
                res.status(Status.INTERNAL_SERVER_ERROR_500)
                        .send();
            }
        }
        var responseTracing = tracing.responseTracing();
        try {
            if (definition.isAudited()) {
                audit(req, res, resourceType, definition, securityContext);
            }
        } finally {
            responseTracing.finish();
        }
        return result;
    }

    private <T> T proceedGeneric(InterceptionContext ctx,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object[] args) throws Exception {
        HttpSecurityDefinition definition = methodSecurity(ctx);

        if (definition.noSecurity()) {
            return chain.proceed(args);
        }

        Context context = Contexts.context()
                .orElseThrow(() -> new IllegalStateException("No security context in active context. "
                                                                     + "Make sure the ContextFeature is added to WebServer"));
        ScopedSecurityContext securityContext = context.get(SecurityContext.class)
                .map(ScopedSecurityContext::new)
                .orElseThrow(() -> new IllegalStateException("No security context in active context. "
                                                                     + "Make sure the ContextFeature is added to WebServer"));
        SecurityEnvironment previousEnvironment = securityContext.env();
        EndpointConfig previousEndpointConfig = securityContext.endpointConfig();
        String resourceType = ctx.serviceInfo().serviceType().fqName();
        SecurityEnvironment securityEnvironment = previousEnvironment.derive()
                .addAttribute("resourceType", resourceType)
                .addAttribute("entryPoint", ctx.elementInfo().elementName())
                .build();
        var ec = EndpointConfig.builder()
                .securityLevels(definition.securityLevels())
                .build();

        boolean success = false;
        try {
            context.register(securityContext);
            securityContext.env(securityEnvironment);
            securityContext.endpointConfig(ec);

            processSecurity(definition, securityContext);
            T result = chain.proceed(args);
            if (definition.atzExplicit() && !securityContext.isAuthorized()) {
                LOGGER.log(Level.ERROR, "Authorization failure. Entry point"
                        + " has failed, it was marked for explicit authorization, "
                        + "yet authorization was never called on security context. The "
                        + "method was invoked and may have changed data."
                        + " Endpoint: " + ctx.serviceInfo().serviceType().fqName() + ", method: "
                        + ctx.elementInfo().elementName());
                throw new SecurityException(securityFailureMessage());
            }
            success = true;
            return result;
        } finally {
            try {
                if (definition.isAudited()) {
                    audit(ctx, success ? "OK" : "DENIED", resourceType, definition, securityContext);
                }
            } finally {
                securityContext.env(previousEnvironment);
                securityContext.endpointConfig(previousEndpointConfig);
                context.unregister(securityContext);
            }
        }
    }

    private void processSecurity(ServerRequest req,
                                 ServerResponse res,
                                 HttpSecurityInterceptorContext ictx,
                                 SecurityTracing tracing,
                                 HttpSecurityDefinition definition,
                                 SecurityContext securityContext) {
        authenticate(req, res, ictx, tracing, definition, securityContext);
        if (ictx.shouldFinish()) {
            return;
        }
        ictx.clearTrace();

        authorize(req, res, ictx, tracing, definition, securityContext);
    }

    private void processSecurity(HttpSecurityDefinition definition,
                                 SecurityContext securityContext) {
        var ictx = new HttpSecurityInterceptorContext();
        authenticate(ictx, definition, securityContext);
        if (ictx.shouldFinish()) {
            return;
        }
        ictx.clearTrace();

        authorize(ictx, definition, securityContext);
    }

    private void authorize(ServerRequest req,
                           ServerResponse res,
                           HttpSecurityInterceptorContext ictx,
                           SecurityTracing tracing,
                           HttpSecurityDefinition definition,
                           SecurityContext securityContext) {
        if (definition.atzExplicit()) {
            // authorization is explicitly done by user, we MUST skip it here
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Endpoint {0} uses explicit authorization, skipping", req.path().absolute().path());
            }
            return;
        }

        AtzTracing atzTracing = tracing.atzTracing();
        try {
            //now authorize (also authorize anonymous requests, as we may have a path-based authorization that allows public
            // access
            if (definition.requiresAuthorization()) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Endpoint {0} requires authorization", req.path().absolute().path());
                }
                SecurityClientBuilder<AuthorizationResponse> clientBuilder = securityContext.atzClientBuilder()
                        .tracingSpan(atzTracing.findParent().orElse(null))
                        .explicitProvider(definition.authorizer());

                processAuthorization(req, res, ictx, clientBuilder);
            } else {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Endpoint {0} does not require authorization. Method security: {1}",
                               req.path().absolute().path(),
                               definition);
                }
            }
        } finally {
            if (ictx.traceSuccess()) {
                atzTracing.finish();
            } else {
                Throwable throwable = ictx.traceThrowable();
                if (null == throwable) {
                    atzTracing.error(ictx.traceDescription());
                } else {
                    atzTracing.error(throwable);
                }
            }
        }
    }

    private void processAuthorization(ServerRequest req,
                                      ServerResponse res,
                                      HttpSecurityInterceptorContext ictx,
                                      SecurityClientBuilder<AuthorizationResponse> clientBuilder) {
        // now fully synchronous
        AuthorizationResponse response = clientBuilder.submit();
        SecurityResponse.SecurityStatus responseStatus = response.status();

        switch (responseStatus) {
        case SUCCESS -> {
            //everything is fine, we can continue with processing
        }
        case FAILURE_FINISH -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse(responseStatus.toString()));
            ictx.traceThrowable(response.throwable().orElse(null));
            ictx.shouldFinish(true);
            abortRequest(res, ictx, response, status(response.statusCode(), Status.FORBIDDEN_403), Map.of());
        }
        case SUCCESS_FINISH -> {
            ictx.shouldFinish(true);
            abortRequest(res, ictx, response, status(response.statusCode(), Status.OK_200), Map.of());
        }
        case FAILURE -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse(responseStatus.toString()));
            ictx.traceThrowable(response.throwable().orElse(null));
            ictx.shouldFinish(true);
            abortRequest(res,
                         ictx,
                         response,
                         status(response.statusCode(), Status.FORBIDDEN_403),
                         Map.of());
        }
        case ABSTAIN -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse(responseStatus.toString()));
            ictx.shouldFinish(true);
            abortRequest(res,
                         ictx,
                         response,
                         status(response.statusCode(), Status.FORBIDDEN_403),
                         Map.of());
        }
        //noinspection DuplicatedCode
        default -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            ictx.shouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            ictx.traceThrowable(throwable);
            throw throwable;
        }
        }
    }

    private void authorize(HttpSecurityInterceptorContext ictx,
                           HttpSecurityDefinition definition,
                           SecurityContext securityContext) {
        if (definition.atzExplicit()) {
            return;
        }

        if (definition.requiresAuthorization()) {
            SecurityClientBuilder<AuthorizationResponse> clientBuilder = securityContext.atzClientBuilder()
                    .explicitProvider(definition.authorizer());

            processAuthorization(ictx, clientBuilder);
        }
    }

    private void processAuthorization(HttpSecurityInterceptorContext ictx,
                                      SecurityClientBuilder<AuthorizationResponse> clientBuilder) {
        AuthorizationResponse response = clientBuilder.submit();
        SecurityResponse.SecurityStatus responseStatus = response.status();

        switch (responseStatus) {
        case SUCCESS -> {
            // everything is fine, we can continue with processing
        }
        case FAILURE_FINISH, SUCCESS_FINISH, FAILURE, ABSTAIN -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse(responseStatus.toString()));
            ictx.traceThrowable(response.throwable().orElse(null));
            ictx.shouldFinish(true);
            abortEntryPoint(response);
        }
        default -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            ictx.shouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            ictx.traceThrowable(throwable);
            throw throwable;
        }
        }
    }

    private void authenticate(ServerRequest req,
                              ServerResponse res,
                              HttpSecurityInterceptorContext ictx,
                              SecurityTracing tracing,
                              HttpSecurityDefinition definition,
                              SecurityContext securityContext) {

        AtnTracing atnTracing = tracing.atnTracing();
        try {
            if (!definition.requiresAuthentication()) {
                return;
            }
            var clientBuilder = securityContext
                    .atnClientBuilder()
                    .optional(definition.authenticationOptional())
                    .update(it -> atnTracing.findParent().ifPresent(it::tracingSpan))
                    .update(it -> definition.authenticator().ifPresent(it::explicitProvider));
            authenticate(req, res, ictx, atnTracing, definition, clientBuilder);
        } finally {
            if (ictx.traceSuccess()) {
                securityContext.user()
                        .ifPresent(atnTracing::logUser);

                securityContext.service()
                        .ifPresent(atnTracing::logService);

                atnTracing.finish();
            } else {
                Throwable ctxThrowable = ictx.traceThrowable();
                if (null == ctxThrowable) {
                    atnTracing.error(ictx.traceDescription());
                } else {
                    atnTracing.error(ctxThrowable);
                }
            }
        }
    }

    private void authenticate(ServerRequest req,
                              ServerResponse res,
                              HttpSecurityInterceptorContext ictx,
                              AtnTracing atnTracing,
                              HttpSecurityDefinition methodSecurity,
                              SecurityClientBuilder<AuthenticationResponse> clientBuilder) {
        AuthenticationResponse response = clientBuilder.submit();

        SecurityResponse.SecurityStatus responseStatus = response.status();

        atnTracing.logStatus(responseStatus);

        switch (responseStatus) {
        case SUCCESS -> {
            response.requestHeaders()
                    .forEach((name, values) -> res.header(name, values.toArray(new String[0])));
        }
        case FAILURE_FINISH -> {
            if (methodSecurity.authenticationOptional()) {
                LOGGER.log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            } else {
                ictx.traceSuccess(false);
                ictx.traceDescription(response.description().orElse(responseStatus.toString()));
                ictx.traceThrowable(response.throwable().orElse(null));
                ictx.shouldFinish(true);

                Status status = status(response.statusCode(), Status.UNAUTHORIZED_401);

                abortRequest(res, ictx, response, status, Map.of());
            }
        }
        case SUCCESS_FINISH -> {
            ictx.shouldFinish(true);
            Status status = status(response.statusCode(), Status.OK_200);
            abortRequest(res, ictx, response, status, Map.of());
        }
        case ABSTAIN -> {
            if (methodSecurity.authenticationOptional()) {
                LOGGER.log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            } else {
                ictx.traceSuccess(false);
                ictx.traceDescription(response.description().orElse(responseStatus.toString()));
                ictx.shouldFinish(true);
                abortRequest(res,
                             ictx,
                             response,
                             Status.UNAUTHORIZED_401,
                             Map.of());
            }
        }
        case FAILURE -> {
            if (methodSecurity.authenticationOptional() && !methodSecurity.failOnFailureIfOptional()) {
                LOGGER.log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            } else {
                ictx.traceDescription(response.description().orElse(responseStatus.toString()));
                ictx.traceThrowable(response.throwable().orElse(null));
                ictx.traceSuccess(false);
                abortRequest(res,
                             ictx,
                             response,
                             Status.UNAUTHORIZED_401,
                             Map.of());
                ictx.shouldFinish(true);
            }
        }
        //noinspection DuplicatedCode
        default -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            ictx.shouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            ictx.traceThrowable(throwable);
            throw throwable;
        }
        }
    }

    private void authenticate(HttpSecurityInterceptorContext ictx,
                              HttpSecurityDefinition definition,
                              SecurityContext securityContext) {
        if (!definition.requiresAuthentication()) {
            return;
        }

        var clientBuilder = securityContext
                .atnClientBuilder()
                .optional(definition.authenticationOptional())
                .update(it -> definition.authenticator().ifPresent(it::explicitProvider));
        AuthenticationResponse response = clientBuilder.submit();
        SecurityResponse.SecurityStatus responseStatus = response.status();

        switch (responseStatus) {
        case SUCCESS -> {
            // everything is fine, we can continue with processing
        }
        case FAILURE_FINISH -> {
            if (definition.authenticationOptional()) {
                LOGGER.log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            } else {
                ictx.traceSuccess(false);
                ictx.traceDescription(response.description().orElse(responseStatus.toString()));
                ictx.traceThrowable(response.throwable().orElse(null));
                ictx.shouldFinish(true);
                abortEntryPoint(response);
            }
        }
        case FAILURE -> {
            if (definition.authenticationOptional() && !definition.failOnFailureIfOptional()) {
                LOGGER.log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            } else {
                ictx.traceDescription(response.description().orElse(responseStatus.toString()));
                ictx.traceThrowable(response.throwable().orElse(null));
                ictx.traceSuccess(false);
                ictx.shouldFinish(true);
                abortEntryPoint(response);
            }
        }
        case SUCCESS_FINISH, ABSTAIN -> {
            if (responseStatus == SecurityResponse.SecurityStatus.ABSTAIN && definition.authenticationOptional()) {
                LOGGER.log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
            } else {
                ictx.traceSuccess(false);
                ictx.traceDescription(response.description().orElse(responseStatus.toString()));
                ictx.traceThrowable(response.throwable().orElse(null));
                ictx.shouldFinish(true);
                abortEntryPoint(response);
            }
        }
        default -> {
            ictx.traceSuccess(false);
            ictx.traceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            ictx.shouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            ictx.traceThrowable(throwable);
            throw throwable;
        }
        }
    }

    private void abortRequest(ServerResponse res,
                              HttpSecurityInterceptorContext ictx,
                              SecurityResponse response,
                              Status defaultStatus,
                              Map<String, List<String>> defaultHeaders) {
        Status status = status(response.statusCode(), defaultStatus);
        Map<String, List<String>> responseHeaders = response.responseHeaders();

        var usedHeaders = responseHeaders.isEmpty() ? defaultHeaders : responseHeaders;
        for (Map.Entry<String, List<String>> entry : usedHeaders.entrySet()) {
            res.header(entry.getKey(), entry.getValue().toArray(new String[0]));
        }

        // Run security response mappers if available, or revert to old logic for compatibility
        AtomicReference<String> entity = new AtomicReference<>("Security did not allow this request to proceed");
        if (!responseMappers.isEmpty()) {
            responseMappers.forEach(m -> entity.set(Objects.requireNonNull(m.aborted(res, response, entity.get()))));
        } else if (config.get("debug").asBoolean().orElse(false)) {
            response.description()
                    .ifPresent(entity::set);
        }

        if (config.get("do-not-throw").asBoolean().orElse(false)) {
            if (res.isSent()) {
                res.send(entity.get());
            }
        } else {
            throw new HttpException(entity.get(), status, true);
        }
    }

    private void abortEntryPoint(SecurityResponse response) {
        throw new SecurityException(securityFailureMessage(response), response.throwable().orElse(null));
    }

    private String securityFailureMessage(SecurityResponse response) {
        if (config.get("debug").asBoolean().orElse(false)) {
            return response.description().orElseGet(this::securityFailureMessage);
        }
        return securityFailureMessage();
    }

    private String securityFailureMessage() {
        return "Security did not allow this request to proceed";
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Status status(OptionalInt statusCode, Status defaultStatus) {
        if (statusCode.isPresent()) {
            return Status.create(statusCode.getAsInt());
        }
        return defaultStatus;
    }

    private void audit(ServerRequest req,
                       ServerResponse res,
                       String resourceName,
                       HttpSecurityDefinition methodSecurity,
                       SecurityContext securityContext) {
        AuditEvent.AuditSeverity auditSeverity;
        Status.Family family = res.status()
                .family();
        if (family == Status.Family.SUCCESSFUL) {
            auditSeverity = methodSecurity.auditOkSeverity();
        } else {
            auditSeverity = methodSecurity.auditErrorSeverity();
        }

        SecurityAuditEvent auditEvent = SecurityAuditEvent
                .audit(auditSeverity, methodSecurity.auditEventType(), methodSecurity.auditMessageFormat())
                .addParam(AuditEvent.AuditParam.plain("method", req.prologue().method().text()))
                .addParam(AuditEvent.AuditParam.plain("path", req.path().absolute().path()))
                .addParam(AuditEvent.AuditParam.plain("status", String.valueOf(res.status().codeText())))
                .addParam(AuditEvent.AuditParam.plain("subject",
                                                      securityContext.user()
                                                              .or(securityContext::service)
                                                              .orElse(SecurityContext.ANONYMOUS)))
                .addParam(AuditEvent.AuditParam.plain("transport", "http"))
                .addParam(AuditEvent.AuditParam.plain("resourceType", resourceName))
                .addParam(AuditEvent.AuditParam.plain("targetUri", req.requestedUri().toUri()));

        securityContext.audit(auditEvent);
    }

    private void audit(InterceptionContext ctx,
                       String status,
                       String resourceName,
                       HttpSecurityDefinition methodSecurity,
                       SecurityContext securityContext) {
        SecurityEnvironment env = securityContext.env();
        AuditEvent.AuditSeverity auditSeverity = "OK".equals(status)
                ? methodSecurity.auditOkSeverity()
                : methodSecurity.auditErrorSeverity();

        SecurityAuditEvent auditEvent = SecurityAuditEvent
                .audit(auditSeverity, methodSecurity.auditEventType(), methodSecurity.auditMessageFormat())
                .addParam(AuditEvent.AuditParam.plain("method", env.method()))
                .addParam(AuditEvent.AuditParam.plain("path", env.path()))
                .addParam(AuditEvent.AuditParam.plain("status", status))
                .addParam(AuditEvent.AuditParam.plain("subject",
                                                      securityContext.user()
                                                              .or(securityContext::service)
                                                              .orElse(SecurityContext.ANONYMOUS)))
                .addParam(AuditEvent.AuditParam.plain("transport", env.transport()))
                .addParam(AuditEvent.AuditParam.plain("resourceType", resourceName))
                .addParam(AuditEvent.AuditParam.plain("targetUri", env.targetUri()))
                .addParam(AuditEvent.AuditParam.plain("entryPoint", ctx.elementInfo().elementName()));

        securityContext.audit(auditEvent);
    }

    private HttpSecurityDefinition methodSecurity(InterceptionContext ctx) {
        Signature signature = Signature.create(ctx.serviceInfo().serviceType(), ctx.elementInfo());

        methodSecurityDefinitionLock.readLock().lock();
        try {
            var result = methodSecurityDefinitions.get(signature);
            if (result != null) {
                return result;
            }
        } finally {
            methodSecurityDefinitionLock.readLock().unlock();
        }
        methodSecurityDefinitionLock.writeLock().lock();
        try {
            var result = methodSecurityDefinitions.get(signature);
            if (result != null) {
                return result;
            }
            result = createMethodSecurity(ctx);
            methodSecurityDefinitions.put(signature, result);
            return result;
        } finally {
            methodSecurityDefinitionLock.writeLock().unlock();
        }
    }

    private HttpSecurityDefinition createMethodSecurity(InterceptionContext ctx) {
        TypedElementInfo method = ctx.elementInfo();
        HttpSecurityDefinition typeSecurity = typeSecurity(ctx);
        HttpSecurityDefinition methodSecurity = typeSecurity.copy();

        securityAnnotations(methodSecurity, method.annotations());
        SecurityLevel currentLevel = methodSecurity.lastSecurityLevel();

        currentLevel = SecurityLevel.builder()
                .from(currentLevel)
                .methodName(method.elementName())
                .methodAnnotations(method.annotations())
                .build();
        methodSecurity.lastSecurityLevel(currentLevel);

        for (AnnotationAnalyzer analyzer : annotationAnalyzers) {
            methodSecurity.analyzerResponse(analyzer,
                                            analyzer.analyze(ctx.serviceInfo().serviceType(),
                                                             method.annotations(),
                                                             typeSecurity.analyzerResponse(analyzer)));
        }

        return methodSecurity;
    }

    private void securityAnnotations(HttpSecurityDefinition definition, List<Annotation> annotations) {
        Annotations.findFirst(AUTHENTICATED_TYPE, annotations)
                .ifPresent(definition::authenticated);
        Annotations.findFirst(AUTHORIZED_TYPE, annotations)
                .ifPresent(definition::authorized);
        Annotations.findFirst(AUDITED_TYPE, annotations)
                .ifPresent(definition::audited);
    }

    private HttpSecurityDefinition typeSecurity(InterceptionContext ctx) {
        TypeName typeName = ctx.serviceInfo().serviceType();

        typeSecurityDefinitionLock.readLock().lock();
        try {
            var result = typeSecurityDefinitions.get(typeName);
            if (result != null) {
                return result;
            }
        } finally {
            typeSecurityDefinitionLock.readLock().unlock();
        }
        typeSecurityDefinitionLock.writeLock().lock();
        try {
            var result = typeSecurityDefinitions.get(typeName);
            if (result != null) {
                return result;
            }
            result = createTypeSecurity(ctx);
            typeSecurityDefinitions.put(typeName, result);
            return result;
        } finally {
            typeSecurityDefinitionLock.writeLock().unlock();
        }
    }

    private HttpSecurityDefinition createTypeSecurity(InterceptionContext ctx) {
        HttpSecurityDefinition definition = new HttpSecurityDefinition();

        config.get("authenticate-annotated-only").asBoolean().ifPresent(definition::requiresAuthentication);
        config.get("authorize-annotated-only").asBoolean().ifPresent(definition::authorizeAnnotatedOnly);
        config.get("fail-on-failure-if-optional").asBoolean().ifPresent(definition::failOnFailureIfOptional);

        List<Annotation> typeAnnotations = ctx.typeAnnotations();

        securityAnnotations(definition, typeAnnotations);

        SecurityLevel securityLevel = SecurityLevel.builder()
                .type(ctx.serviceInfo().serviceType())
                .classAnnotations(typeAnnotations)
                .build();
        definition.addSecurityLevel(securityLevel);

        for (AnnotationAnalyzer analyzer : annotationAnalyzers) {
            definition.analyzerResponse(analyzer, analyzer.analyze(ctx.serviceInfo().serviceType(), typeAnnotations));
        }

        return definition;
    }

    private static final class ScopedSecurityContext implements SecurityContext {
        private final SecurityContext delegate;
        private final AtomicBoolean authorized = new AtomicBoolean();

        private ScopedSecurityContext(SecurityContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public SecurityRequestBuilder<?> securityRequestBuilder() {
            return delegate.securityRequestBuilder();
        }

        @Override
        public SecurityRequestBuilder<?> securityRequestBuilder(SecurityEnvironment environment) {
            return delegate.securityRequestBuilder(environment);
        }

        @Override
        public SecurityClientBuilder<AuthenticationResponse> atnClientBuilder() {
            return delegate.atnClientBuilder();
        }

        @Override
        public AuthenticationResponse authenticate() {
            return delegate.authenticate();
        }

        @Override
        public SecurityClientBuilder<AuthorizationResponse> atzClientBuilder() {
            authorized.set(true);
            return delegate.atzClientBuilder();
        }

        @Override
        public OutboundSecurityClientBuilder outboundClientBuilder() {
            return delegate.outboundClientBuilder();
        }

        @Override
        public AuthorizationResponse authorize(Object... resource) {
            authorized.set(true);
            return delegate.authorize(resource);
        }

        @Override
        public boolean isAuthenticated() {
            return delegate.isAuthenticated();
        }

        @Override
        public boolean isAuthorized() {
            return authorized.get();
        }

        @Override
        public void logout() {
            delegate.logout();
        }

        @Override
        public boolean isUserInRole(String role, String authorizerName) {
            return delegate.isUserInRole(role, authorizerName);
        }

        @Override
        public boolean isUserInRole(String role) {
            return delegate.isUserInRole(role);
        }

        @Override
        public void audit(AuditEvent event) {
            delegate.audit(event);
        }

        @Override
        public Optional<Subject> service() {
            return delegate.service();
        }

        @Override
        public Optional<Subject> user() {
            return delegate.user();
        }

        @Override
        public void runAs(Subject subject, Runnable runnable) {
            delegate.runAs(subject, runnable);
        }

        @Override
        public void runAs(String role, Runnable runnable) {
            delegate.runAs(role, runnable);
        }

        @Override
        public Optional<Principal> userPrincipal() {
            return delegate.userPrincipal();
        }

        @Override
        public Optional<Principal> servicePrincipal() {
            return delegate.servicePrincipal();
        }

        @Override
        public SpanContext tracingSpan() {
            return delegate.tracingSpan();
        }

        @Override
        public Tracer tracer() {
            return delegate.tracer();
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public SecurityTime serverTime() {
            return delegate.serverTime();
        }

        @Override
        public SecurityEnvironment env() {
            return delegate.env();
        }

        @Override
        public void env(SecurityEnvironment env) {
            delegate.env(env);
        }

        @Override
        public EndpointConfig endpointConfig() {
            return delegate.endpointConfig();
        }

        @Override
        public void endpointConfig(EndpointConfig ec) {
            delegate.endpointConfig(ec);
        }
    }

    private record Signature(TypeName declaringType,
                             String methodName,
                             List<TypeName> parameterTypes) {
        static Signature create(TypeName declaringType, TypedElementInfo method) {
            return new Signature(declaringType,
                                 method.elementName(),
                                 method.parameterArguments().stream()
                                         .map(TypedElementInfo::typeName)
                                         .collect(Collectors.toUnmodifiableList()));
        }
    }
}

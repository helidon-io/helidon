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

package io.helidon.security.jersey;

import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Path;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.security.AuditEvent;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Entity;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.annot.Audited;
import io.helidon.security.annot.Authenticated;
import io.helidon.security.annot.Authorized;
import io.helidon.security.internal.SecurityAuditEvent;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerConfig;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.ResourceMethod;

import static io.helidon.security.EndpointConfig.AnnotationScope.APPLICATION;
import static io.helidon.security.EndpointConfig.AnnotationScope.CLASS;
import static io.helidon.security.EndpointConfig.AnnotationScope.METHOD;

/**
 * A filter that handles authentication and authorization.
 */
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
public class SecurityFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String PROP_FILTER_CONTEXT = "io.helidon.security.jersey.FilterContext";

    private static final Logger LOGGER = Logger.getLogger(SecurityFilter.class.getName());

    private final Map<Class<?>, SecurityDefinition> resourceClassSecurity = new ConcurrentHashMap<>();
    private final Map<Method, SecurityDefinition> resourceMethodSecurity = new ConcurrentHashMap<>();

    @Context
    private FeatureConfig featureConfig;

    @Context
    private Security security;

    @Context
    private ServerConfig serverConfig;

    @Context
    private ResourceInfo resourceInfo;

    @Context
    private UriInfo uriInfo;

    @Context
    private SecurityContext securityContext;

    // The filter is in singleton scope, so caching in an instance field is OK
    private SecurityDefinition appWideSecurity;

    /**
     * Default constructor to be used by Jersey when creating an instance of this class.
     */
    public SecurityFilter() {
    }

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilter(FeatureConfig featureConfig,
                   Security security,
                   ServerConfig serverConfig,
                   ResourceInfo resourceInfo,
                   UriInfo uriInfo,
                   SecurityContext securityContext) {

        this.featureConfig = featureConfig;
        this.security = security;
        this.serverConfig = serverConfig;
        this.resourceInfo = resourceInfo;
        this.uriInfo = uriInfo;
        this.securityContext = securityContext;
    }

    /**
     * A life-cycle method invoked by Jersey that finished initialization of the filter.
     */
    @PostConstruct
    public void postConstruct() {
        Class<?> appClass = getOriginalApplication().getClass();

        this.appWideSecurity = securityForClass(appClass, null);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        Span securitySpan = startNewSpan(securityContext.getTracingSpan(), "security");
        securitySpan.log(CollectionsHelper.mapOf("securityId", securityContext.getId()));
        FilterContext context = initRequestFiltering(request);

        if (context.isShouldFinish()) {
            // 404
            finishSpan(securitySpan, CollectionsHelper.listOf());
            return;
        }

        // The following two lines are not possible in JAX-RS or Jersey - we would have to touch
        // underlying web server's request...
        //.addAttribute("userIp", req.remoteAddress())
        //.addAttribute("userPort", req.remotePort())
        URI requestUri = request.getUriInfo().getRequestUri();
        String query = requestUri.getQuery();
        String origRequest;
        if ((null == query) || query.isEmpty()) {
            origRequest = requestUri.getPath();
        } else {
            origRequest = requestUri.getPath() + "?" + query;
        }
        Map<String, List<String>> allHeaders = new HashMap<>(context.getHeaders());
        allHeaders.put(Security.HEADER_ORIG_URI, CollectionsHelper.listOf(origRequest));

        SecurityEnvironment env = SecurityEnvironment.builder(security.getServerTime())
                .path(context.getResourcePath())
                .targetUri(context.getTargetUri())
                .method(context.getMethod())
                .headers(allHeaders)
                .addAttribute("resourceType", context.getResourceName())
                .build();

        EndpointConfig ec = EndpointConfig.builder()
                .annotations(APPLICATION, context.getMethodSecurity().getApplicationScope())
                .annotations(CLASS, context.getMethodSecurity().getResourceScope())
                .annotations(METHOD, context.getMethodSecurity().getOperationScope())
                .build();

        try {
            securityContext.setEnv(env);
            securityContext.setEndpointConfig(ec);
            doFilter(request, context, securitySpan, securityContext);
        } finally {
            if (context.isTraceSuccess()) {
                finishSpan(securitySpan, CollectionsHelper.listOf());
            } else {
                // failed
                HttpUtil.traceError(securitySpan, null, "aborted");
            }
        }
    }

    private void doFilter(ContainerRequestContext request,
                          FilterContext filterContext,
                          Span securitySpan, SecurityContext securityContext) {
        request.setProperty(PROP_FILTER_CONTEXT, filterContext);

        // security not configured or 404
        if (filterContext.isShouldFinish()) {
            LOGGER.finest(() -> "Filter aborting, not configured security or 404");
            return;
        }

        //context is needed even if authn/authz fails - for auditing
        request.setSecurityContext(new JerseySecurityContext(securityContext,
                                                             filterContext.getMethodSecurity(),
                                                             "https".equals(filterContext.getTargetUri().getScheme())));


        /*
         * Authentication
         */
        authenticate(filterContext, securitySpan, securityContext);

        LOGGER.finest(() -> "Filter after authentication. Should finish: " + filterContext.isShouldFinish());

        // authentication failed
        if (filterContext.isShouldFinish()) {
            return;
        }

        filterContext.setTraceSuccess(true);
        filterContext.setTraceDescription(null);
        filterContext.setTraceThrowable(null);

        /*
         * Authorization
         */
        authorize(filterContext, securitySpan, securityContext);

        LOGGER.finest(() -> "Filter completed (after authorization)");
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        javax.ws.rs.core.SecurityContext jSecurityContext = requestContext.getSecurityContext();
        if (null == jSecurityContext) {
            return;
        }

        JerseySecurityContext jerseySecurityContext;
        if (jSecurityContext instanceof JerseySecurityContext) {
            jerseySecurityContext = (JerseySecurityContext) jSecurityContext;
        } else {
            return;
        }

        FilterContext fc = (FilterContext) requestContext.getProperty(PROP_FILTER_CONTEXT);
        SecurityDefinition methodSecurity = jerseySecurityContext.methodSecurity();
        SecurityContext securityContext = jerseySecurityContext.securityContext();

        if (fc.isExplicitAtz() && !securityContext.atzChecked()) {
            // authorization should have been explicit, yet it was not checked - this is a programmer error
            if (featureConfig.isDebug()) {
                responseContext.setEntity("Authorization was marked as explicit, yet it was never called in method");
            } else {
                responseContext.setEntity("");
            }
            responseContext.setStatus(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
            LOGGER.severe("Authorization failure. Request for" + fc.getResourcePath() + " has failed, as it was marked"
                                  + "as explicitly authorized, yet authorization was never called on security context. The method"
                                  + " was invoked and may have changed data. Marking as internal server error");
            fc.setShouldFinish(true);
        } else {
            fc.setShouldFinish(false);
        }

        SpanContext requestSpan = securityContext.getTracingSpan();
        Span span = startNewSpan(requestSpan, "security:response");

        try {
            if (methodSecurity.isAudited()) {
                AuditEvent.AuditSeverity auditSeverity;
                if (responseContext.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                    auditSeverity = methodSecurity.getAuditOkSeverity();
                } else {
                    auditSeverity = methodSecurity.getAuditErrorSeverity();
                }

                SecurityAuditEvent auditEvent = SecurityAuditEvent
                        .audit(auditSeverity, methodSecurity.getAuditEventType(), methodSecurity.getAuditMessageFormat())
                        .addParam(AuditEvent.AuditParam.plain("method", fc.getMethod()))
                        .addParam(AuditEvent.AuditParam.plain("path", fc.getResourcePath()))
                        .addParam(AuditEvent.AuditParam.plain("status", String.valueOf(responseContext.getStatus())))
                        .addParam(AuditEvent.AuditParam.plain("subject",
                                                              OptionalHelper.from(securityContext.getUser())
                                                                      .or(securityContext::getService)
                                                                      .asOptional()
                                                                      .orElse(SecurityContext.ANONYMOUS)))
                        .addParam(AuditEvent.AuditParam.plain("transport", "http"))
                        .addParam(AuditEvent.AuditParam.plain("resourceType", fc.getResourceName()))
                        .addParam(AuditEvent.AuditParam.plain("targetUri", fc.getTargetUri()));

                securityContext.audit(auditEvent);
            }

            if (!fc.isShouldFinish() && (fc.getResponseMessage().filterFunction != null)) {
                OutputStream originalStream = responseContext.getEntityStream();

                OutputStreamPublisher outputStreamPublisher = new OutputStreamPublisher();
                responseContext.setEntityStream(outputStreamPublisher);

                fc.getResponseMessage().filterFunction
                        .apply(outputStreamPublisher)
                        .subscribe(new SubscriberOutputStream(originalStream, outputStreamPublisher::signalCloseComplete));
            }
        } finally {
            finishSpan(span, CollectionsHelper.listOf());
            if ((Boolean) requestContext.getProperty(SecurityPreMatchingFilter.PROP_CLOSE_PARENT_SPAN)) {
                finishSpan((Span) requestContext.getProperty(SecurityPreMatchingFilter.PROP_PARENT_SPAN),
                           CollectionsHelper.listOf());
            }
        }
    }

    private void finishSpan(Span span, List<String> logs) {
        if (null == span) {
            return;
        }
        logs.forEach(span::log);
        span.finish();
    }

    private Span startNewSpan(SpanContext parentSpan, String name) {
        Tracer.SpanBuilder spanBuilder = security.getTracer().buildSpan(name);
        spanBuilder.asChildOf(parentSpan);

        return spanBuilder.start();
    }

    private void authorize(FilterContext context, Span securitySpan, SecurityContext securityContext) {
        if (context.getMethodSecurity().isAtzExplicit()) {
            // authorization is explicitly done by user, we MUST skip it here
            context.setExplicitAtz(true);
            return;
        }
        Span atzSpan = startNewSpan(securitySpan.context(), "security:atz");

        try {
            //now authorize (also authorize anonymous requests, as we may have a path-based authorization that allows public
            // access
            if (context.getMethodSecurity().requiresAuthorization()) {
                SecurityClientBuilder<AuthorizationResponse> clientBuilder = securityContext.atzClientBuilder()
                        .tracingSpan(atzSpan)
                        .explicitProvider(context.getMethodSecurity().getAuthorizer());

                processAuthorization(context, clientBuilder);
            }
        } finally {
            if (!context.isTraceSuccess()) {
                HttpUtil.traceError(atzSpan, context.getTraceThrowable(), context.getTraceDescription());
            } else {
                finishSpan(atzSpan, CollectionsHelper.listOf());
            }
        }
    }

    private void authenticate(FilterContext context, Span securitySpan, SecurityContext securityContext) {
        Span atnSpan = startNewSpan(securitySpan.context(), "security:atn");

        try {
            if (context.getMethodSecurity().requiresAuthentication()) {
                //authenticate request
                SecurityClientBuilder<AuthenticationResponse> clientBuilder = securityContext
                        .atnClientBuilder()
                        .optional(context.getMethodSecurity().authenticationOptional())
                        .requestMessage(toRequestMessage(context))
                        .responseMessage(context.getResponseMessage())
                        .tracingSpan(atnSpan);

                clientBuilder.explicitProvider(context.getMethodSecurity().getAuthenticator());
                processAuthentication(context, clientBuilder, context.getMethodSecurity());
            }
        } finally {
            if (context.isTraceSuccess()) {
                List<String> logs = new LinkedList<>();
                securityContext.getUser()
                        .ifPresent(user -> logs.add("security.user: " + user.getPrincipal().getName()));
                securityContext.getService()
                        .ifPresent(service -> logs.add("security.service: " + service.getPrincipal().getName()));

                finishSpan(atnSpan, logs);
            } else {
                HttpUtil.traceError(atnSpan, context.getTraceThrowable(), context.getTraceDescription());
            }
        }
    }

    private SecurityDefinition securityForClass(Class<?> theClass, SecurityDefinition parent) {
        Authenticated atn = theClass.getAnnotation(Authenticated.class);
        Authorized atz = theClass.getAnnotation(Authorized.class);
        Audited audited = theClass.getAnnotation(Audited.class);

        // as sometimes we may want to prevent calls to authorization provider unless
        // explicitly invoked by developer
        SecurityDefinition definition = (
                (null == parent)
                        ? new SecurityDefinition(featureConfig.shouldAuthorizeAnnotatedOnly())
                        : parent.copyMe());
        definition.add(atn);
        definition.add(atz);
        definition.add(audited);

        //this is specific jersey implementation - if parent is null, this is application scope, otherwise resource scope
        Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap = (
                (null == parent)
                        ? definition.getApplicationScope()
                        : definition.getResourceScope());

        addCustomAnnotations(customAnnotsMap, theClass);

        return definition;
    }

    private FilterContext initRequestFiltering(ContainerRequestContext requestContext) {
        FilterContext context = new FilterContext();

        Method definitionMethod = getDefinitionMethod(requestContext);

        if (definitionMethod == null) {
            // this will end in 404, just let it on
            context.setShouldFinish(true);
            return context;
        }

        context.setMethodSecurity(getMethodSecurity(definitionMethod));
        context.setResourceName(definitionMethod.getDeclaringClass().getSimpleName());
        context.setMethod(requestContext.getMethod());
        context.setHeaders(HttpUtil.toSimpleMap(requestContext.getHeaders()));
        context.setTargetUri(requestContext.getUriInfo().getRequestUri());
        context.setResourcePath(context.getTargetUri().getPath());

        context.setJerseyRequest((ContainerRequest) requestContext);

        // now extract headers
        featureConfig.getQueryParamHandlers()
                .forEach(handler -> handler.extract(uriInfo, context.getHeaders()));

        return context;
    }

    private void processAuthorization(FilterContext context,
                                      SecurityClientBuilder<AuthorizationResponse> clientBuilder) {
        // now fully synchronous
        AuthorizationResponse response = clientBuilder.get();
        SecurityResponse.SecurityStatus responseStatus = response.getStatus();

        switch (responseStatus) {
        case SUCCESS:
            //everything is fine, we can continue with processing
            return;
        case FAILURE_FINISH:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.getDescription().orElse(responseStatus.toString()));
            context.setTraceThrowable(response.getThrowable().orElse(null));
            context.setShouldFinish(true);
            int status = response.getStatusCode().orElse(Response.Status.FORBIDDEN.getStatusCode());
            abortRequest(context, response, status, CollectionsHelper.mapOf());
            return;
        case SUCCESS_FINISH:
            context.setShouldFinish(true);
            status = response.getStatusCode().orElse(Response.Status.OK.getStatusCode());
            abortRequest(context, response, status, CollectionsHelper.mapOf());
            return;
        case FAILURE:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.getDescription().orElse(responseStatus.toString()));
            context.setTraceThrowable(response.getThrowable().orElse(null));
            context.setShouldFinish(true);
            abortRequest(context,
                         response,
                         response.getStatusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                         CollectionsHelper.mapOf());
            return;
        case ABSTAIN:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.getDescription().orElse(responseStatus.toString()));
            context.setShouldFinish(true);
            abortRequest(context,
                         response,
                         response.getStatusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                         CollectionsHelper.mapOf());
            return;
        default:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.getDescription().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            context.setShouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            context.setTraceThrowable(throwable);
            throw throwable;
        }
    }

    void processAuthentication(FilterContext context,
                                       SecurityClientBuilder<AuthenticationResponse> clientBuilder,
                                       SecurityDefinition methodSecurity) {

        AuthenticationResponse response = clientBuilder.get();

        SecurityResponse.SecurityStatus responseStatus = response.getStatus();

        switch (responseStatus) {
        case SUCCESS:
            //everything is fine, we can continue with processing
            return;
        case FAILURE_FINISH:
            if (methodSecurity.authenticationOptional()) {
                LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            } else {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.getDescription().orElse(responseStatus.toString()));
                context.setTraceThrowable(response.getThrowable().orElse(null));
                context.setShouldFinish(true);

                int status = response.getStatusCode().orElse(Response.Status.UNAUTHORIZED.getStatusCode());
                abortRequest(context, response, status, CollectionsHelper.mapOf());
            }

            return;
        case SUCCESS_FINISH:
            context.setShouldFinish(true);

            int status = response.getStatusCode().orElse(Response.Status.OK.getStatusCode());
            abortRequest(context, response, status, CollectionsHelper.mapOf());

            return;
        case ABSTAIN:
            if (methodSecurity.authenticationOptional()) {
                LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            } else {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.getDescription().orElse(responseStatus.toString()));
                context.setShouldFinish(true);
                abortRequest(context,
                             response,
                             Response.Status.UNAUTHORIZED.getStatusCode(),
                             CollectionsHelper.mapOf());
            }
            return;
        case FAILURE:
            if (methodSecurity.authenticationOptional()) {
                LOGGER.finest("Authentication failed, but was optional, so assuming anonymous");
            } else {
                context.setTraceDescription(response.getDescription().orElse(responseStatus.toString()));
                context.setTraceThrowable(response.getThrowable().orElse(null));
                context.setTraceSuccess(false);
                abortRequest(context,
                             response,
                             Response.Status.UNAUTHORIZED.getStatusCode(),
                             CollectionsHelper.mapOf());
                context.setShouldFinish(true);
            }
            return;
        default:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.getDescription().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            context.setShouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            context.setTraceThrowable(throwable);
            throw throwable;
        }
    }

    private void abortRequest(FilterContext context,
                              SecurityResponse response,
                              int defaultStatusCode,
                              Map<String, List<String>> defaultHeaders) {

        int statusCode = response.getStatusCode().orElse(defaultStatusCode);
        Map<String, List<String>> responseHeaders = response.getResponseHeaders();

        Response.ResponseBuilder responseBuilder = Response.status(statusCode);
        if (responseHeaders.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : defaultHeaders.entrySet()) {
                responseBuilder.header(entry.getKey(), entry.getValue());
            }
        } else {
            updateHeaders(responseHeaders, responseBuilder);
        }

        if (featureConfig.isDebug()) {
            response.getDescription().ifPresent(responseBuilder::entity);
        }

        context.getJerseyRequest().abortWith(responseBuilder.build());
    }

    private void updateHeaders(Map<String, List<String>> responseHeaders, Response.ResponseBuilder responseBuilder) {
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                responseBuilder.header(entry.getKey(), value);
            }
        }
    }

    private SecurityDefinition getMethodSecurity(Method definitionMethod) {
        // Check cache
        if (resourceMethodSecurity.containsKey(definitionMethod)) {
            return resourceMethodSecurity.get(definitionMethod);
        }

        // Jersey model 'definition method' is the method that contains JAX-RS/Jersey annotations. JAX-RS does not support
        // merging annotations from a parent, so we don't have to look for annotations on corresponding methods of interfaces
        // and abstract classes implemented by the definition method.

        // Jersey model does not have a 'definition class', so we have to find it from a handler class
        Class<?> definitionClass = getDefinitionClass(resourceInfo.getResourceClass());

        SecurityDefinition definition = this.resourceClassSecurity
                .computeIfAbsent(definitionClass, aClass -> securityForClass(definitionClass, appWideSecurity));

        Authenticated atn = definitionMethod.getAnnotation(Authenticated.class);
        Authorized atz = definitionMethod.getAnnotation(Authorized.class);
        Audited audited = definitionMethod.getAnnotation(Audited.class);

        SecurityDefinition methodDef = definition.copyMe();
        methodDef.add(atn);
        methodDef.add(atz);
        methodDef.add(audited);

        addCustomAnnotations(methodDef.getOperationScope(), definitionMethod);

        resourceMethodSecurity.put(definitionMethod, methodDef);

        return methodDef;
    }

    private void addCustomAnnotations(Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap, Class<?> theClass) {
        Annotation[] annotations = theClass.getAnnotations();
        for (Annotation annotation : annotations) {
            addToMap(annotation.getClass(), customAnnotsMap, annotation);
        }
    }

    private void addToMap(Class<? extends Annotation> annotClass,
                          Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap,
                          Annotation... annot) {
        customAnnotsMap.computeIfAbsent(annotClass, key -> new LinkedList<>()).addAll(Arrays.asList(annot));
    }

    private void addCustomAnnotations(Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap, Method theMethod) {
        Annotation[] annotations = theMethod.getAnnotations();
        for (Annotation annotation : annotations) {
            addToMap(annotation.annotationType(), customAnnotsMap, annotation);
        }
    }

    /**
     * The term 'definition method' used by the Jersey model means the method that contains JAX-RS/Jersey annotations.
     */
    private Method getDefinitionMethod(ContainerRequestContext requestContext) {
        if (!(requestContext.getUriInfo() instanceof ExtendedUriInfo)) {
            throw new IllegalStateException("Could not get Extended Uri Info. Incompatible version of Jersey?");
        }

        ExtendedUriInfo uriInfo = (ExtendedUriInfo) requestContext.getUriInfo();
        ResourceMethod matchedResourceMethod = uriInfo.getMatchedResourceMethod();
        Invocable invocable = matchedResourceMethod.getInvocable();
        return invocable.getDefinitionMethod();
    }

    // taken from org.glassfish.jersey.server.model.internal.ModelHelper#getAnnotatedResourceClass
    private Class<?> getDefinitionClass(Class<?> resourceClass) {
        Class<?> foundInterface = null;

        // traverse the class hierarchy to find the annotation
        // According to specification, annotation in the super-classes must take precedence over annotation in the
        // implemented interfaces
        Class<?> cls = resourceClass;
        do {
            if (cls.isAnnotationPresent(Path.class)) {
                return cls;
            }

            // if no annotation found on the class currently traversed, check for annotation in the interfaces on this
            // level - if not already previously found
            if (foundInterface == null) {
                for (final Class<?> i : cls.getInterfaces()) {
                    if (i.isAnnotationPresent(Path.class)) {
                        // store the interface reference in case no annotation will be found in the super-classes
                        foundInterface = i;
                        break;
                    }
                }
            }

            cls = cls.getSuperclass();
        } while (cls != null);

        if (foundInterface != null) {
            return foundInterface;
        }

        return resourceClass;
    }

    private Application getOriginalApplication() {
        // Unfortunately the following logic is very "implementation aware". We need the original instance of
        // javax.ws.rs.core.Application to get the @Authenticated annotation instance if present.
        // Jersey server configuration is immutable and a defensive copy is done with every change.
        // However, the original Application instance is always present and hidden deep in the ServerConfig implementation.

        // ResourceConfig is always the implementation of ServerConfig
        if (!(serverConfig instanceof ResourceConfig)) {
            throw new IllegalStateException("Could not get Application instance. Incompatible version of Jersey?");
        }

        ResourceConfig resourceConfig = (ResourceConfig) serverConfig;
        Application application = resourceConfig.getApplication();

        while (application instanceof ResourceConfig) {
            Application wrappedApplication = ((ResourceConfig) application).getApplication();
            //noinspection ObjectEquality
            if (wrappedApplication == application) {
                break;
            }
            application = wrappedApplication;
        }

        return application;
    }

    private Entity toRequestMessage(FilterContext context) {
        switch (context.getMethod().toLowerCase()) {
        case "get":
        case "options":
        case "head":
        case "delete":
            return null;
        default:
            break;
        }

        return filterFunction -> {
            // this is request message (inbound)

            // this will publish bytes coming in from external source (to security provider)
            Flow.Publisher<ByteBuffer> publisherFromJersey =
                    new InputStreamPublisher(context.getJerseyRequest().getEntityStream(), 1024);

            SubscriberInputStream subscriberInputStream = new SubscriberInputStream();
            context.getJerseyRequest().setEntityStream(subscriberInputStream);

            Flow.Publisher<ByteBuffer> publisherToJersey = filterFunction.apply(publisherFromJersey);
            // this will receive request bytes coming in from security provider (filtered)
            publisherToJersey.subscribe(subscriberInputStream);
        };
    }

    static class FilterContext {
        private final JerseyResponseEntity responseMessage = new JerseyResponseEntity();
        private String resourceName;
        private String resourcePath;
        private String method;
        private Map<String, List<String>> headers;
        private URI targetUri;
        private ContainerRequest jerseyRequest;
        private boolean shouldFinish;
        private SecurityDefinition methodSecurity;
        private boolean explicitAtz;

        // tracing support
        private boolean traceSuccess = true;
        private String traceDescription;
        private Throwable traceThrowable;

        JerseyResponseEntity getResponseMessage() {
            return responseMessage;
        }

        String getResourceName() {
            return resourceName;
        }

        void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }

        String getResourcePath() {
            return resourcePath;
        }

        void setResourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        String getMethod() {
            return method;
        }

        void setMethod(String method) {
            this.method = method;
        }

        Map<String, List<String>> getHeaders() {
            return headers;
        }

        void setHeaders(Map<String, List<String>> headers) {
            this.headers = headers;
        }

        URI getTargetUri() {
            return targetUri;
        }

        void setTargetUri(URI targetUri) {
            this.targetUri = targetUri;
        }

        ContainerRequest getJerseyRequest() {
            return jerseyRequest;
        }

        void setJerseyRequest(ContainerRequest jerseyRequest) {
            this.jerseyRequest = jerseyRequest;
        }

        boolean isShouldFinish() {
            return shouldFinish;
        }

        void setShouldFinish(boolean shouldFinish) {
            this.shouldFinish = shouldFinish;
        }

        SecurityDefinition getMethodSecurity() {
            return methodSecurity;
        }

        void setMethodSecurity(SecurityDefinition methodSecurity) {
            this.methodSecurity = methodSecurity;
        }

        boolean isExplicitAtz() {
            return explicitAtz;
        }

        void setExplicitAtz(boolean explicitAtz) {
            this.explicitAtz = explicitAtz;
        }

        boolean isTraceSuccess() {
            return traceSuccess;
        }

        void setTraceSuccess(boolean traceSuccess) {
            this.traceSuccess = traceSuccess;
        }

        String getTraceDescription() {
            return traceDescription;
        }

        void setTraceDescription(String traceDescription) {
            this.traceDescription = traceDescription;
        }

        Throwable getTraceThrowable() {
            return traceThrowable;
        }

        void setTraceThrowable(Throwable traceThrowable) {
            this.traceThrowable = traceThrowable;
        }
    }

    private static class JerseyResponseEntity implements Entity {
        private volatile Function<Flow.Publisher<ByteBuffer>, Flow.Publisher<ByteBuffer>> filterFunction;

        @Override
        public void filter(Function<Flow.Publisher<ByteBuffer>, Flow.Publisher<ByteBuffer>> filterFunction) {
            this.filterFunction = filterFunction;
        }
    }
}

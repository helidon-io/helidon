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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Entity;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import org.glassfish.jersey.server.ContainerRequest;

import static io.helidon.security.EndpointConfig.AnnotationScope.APPLICATION;
import static io.helidon.security.EndpointConfig.AnnotationScope.CLASS;
import static io.helidon.security.EndpointConfig.AnnotationScope.METHOD;

/**
 * Helper class for security filters.
 */
abstract class SecurityFilterCommon {
    static final String PROP_FILTER_CONTEXT = "io.helidon.security.jersey.FilterContext";

    @Context
    private Security security;

    @Context
    private FeatureConfig featureConfig;

    SecurityFilterCommon() {
    }

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilterCommon(Security security, FeatureConfig featureConfig) {
        this.security = security;
        this.featureConfig = featureConfig;
    }

    protected Span startSecuritySpan(SecurityContext securityContext) {
        Span securitySpan = startNewSpan(securityContext.getTracingSpan(), "security");
        securitySpan.log(CollectionsHelper.mapOf("securityId", securityContext.getId()));
        return securitySpan;
    }

    protected void finishSpan(Span span, List<String> logs) {
        if (null == span) {
            return;
        }
        logs.forEach(span::log);
        span.finish();
    }

    protected Span startNewSpan(SpanContext parentSpan, String name) {
        Tracer.SpanBuilder spanBuilder = security.getTracer().buildSpan(name);
        spanBuilder.asChildOf(parentSpan);

        return spanBuilder.start();
    }

    protected void doFilter(ContainerRequestContext request, SecurityContext securityContext) {
        Span securitySpan = startSecuritySpan(securityContext);

        SecurityFilter.FilterContext filterContext = initRequestFiltering(request);

        if (filterContext.isShouldFinish()) {
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
        Map<String, List<String>> allHeaders = new HashMap<>(filterContext.getHeaders());
        allHeaders.put(Security.HEADER_ORIG_URI, CollectionsHelper.listOf(origRequest));

        SecurityEnvironment env = SecurityEnvironment.builder(security.getServerTime())
                .path(filterContext.getResourcePath())
                .targetUri(filterContext.getTargetUri())
                .method(filterContext.getMethod())
                .headers(allHeaders)
                .addAttribute("resourceType", filterContext.getResourceName())
                .build();

        EndpointConfig ec = EndpointConfig.builder()
                .annotations(APPLICATION, filterContext.getMethodSecurity().getApplicationScope())
                .annotations(CLASS, filterContext.getMethodSecurity().getResourceScope())
                .annotations(METHOD, filterContext.getMethodSecurity().getOperationScope())
                .build();

        try {
            securityContext.setEnv(env);
            securityContext.setEndpointConfig(ec);

            request.setProperty(PROP_FILTER_CONTEXT, filterContext);
            //context is needed even if authn/authz fails - for auditing
            request.setSecurityContext(new JerseySecurityContext(securityContext,
                                                                 filterContext.getMethodSecurity(),
                                                                 "https".equals(filterContext.getTargetUri().getScheme())));

            processSecurity(request, filterContext, securitySpan, securityContext);
        } finally {
            if (filterContext.isTraceSuccess()) {
                finishSpan(securitySpan, CollectionsHelper.listOf());
            } else {
                // failed
                HttpUtil.traceError(securitySpan, null, "aborted");
            }
        }
    }

    protected void authenticate(SecurityFilter.FilterContext context, Span securitySpan, SecurityContext securityContext) {
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

    protected Entity toRequestMessage(SecurityFilter.FilterContext context) {
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

    protected void processAuthentication(SecurityFilter.FilterContext context,
                                         SecurityClientBuilder<AuthenticationResponse> clientBuilder,
                                         SecurityDefinition methodSecurity) {

        AuthenticationResponse response = clientBuilder.buildAndGet();

        SecurityResponse.SecurityStatus responseStatus = response.getStatus();

        switch (responseStatus) {
        case SUCCESS:
            //everything is fine, we can continue with processing
            return;
        case FAILURE_FINISH:
            if (methodSecurity.authenticationOptional()) {
                logger().finest("Authentication failed, but was optional, so assuming anonymous");
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
                logger().finest("Authentication failed, but was optional, so assuming anonymous");
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
                logger().finest("Authentication failed, but was optional, so assuming anonymous");
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

    protected abstract Logger logger();

    protected void authorize(SecurityFilter.FilterContext context, Span securitySpan, SecurityContext securityContext) {
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

    protected void processAuthorization(SecurityFilter.FilterContext context,
                                        SecurityClientBuilder<AuthorizationResponse> clientBuilder) {
        // now fully synchronous
        AuthorizationResponse response = clientBuilder.buildAndGet();
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

    protected void abortRequest(SecurityFilter.FilterContext context,
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

    protected void updateHeaders(Map<String, List<String>> responseHeaders, Response.ResponseBuilder responseBuilder) {
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                responseBuilder.header(entry.getKey(), value);
            }
        }
    }

    protected Security security() {
        return security;
    }

    protected FeatureConfig featureConfig() {
        return featureConfig;
    }

    protected abstract void processSecurity(ContainerRequestContext request,
                                            SecurityFilter.FilterContext context,
                                            Span securitySpan,
                                            SecurityContext securityContext);

    protected abstract SecurityFilter.FilterContext initRequestFiltering(ContainerRequestContext requestContext);

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

        void clearTrace() {
            setTraceSuccess(true);
            setTraceDescription(null);
            setTraceThrowable(null);
        }
    }

    protected static class JerseyResponseEntity implements Entity {
        private volatile Function<Flow.Publisher<ByteBuffer>, Flow.Publisher<ByteBuffer>> filterFunction;

        @Override
        public void filter(Function<Flow.Publisher<ByteBuffer>, Flow.Publisher<ByteBuffer>> filterFunction) {
            this.filterFunction = filterFunction;
        }

        protected Function<Flow.Publisher<ByteBuffer>, Flow.Publisher<ByteBuffer>> filterFunction() {
            return filterFunction;
        }
    }
}

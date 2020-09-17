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
package io.helidon.security.integration.jersey;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityClientBuilder;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.AtnTracing;
import io.helidon.security.integration.common.AtzTracing;
import io.helidon.security.integration.common.SecurityTracing;

import org.glassfish.jersey.server.ContainerRequest;

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

    protected void doFilter(ContainerRequestContext request, SecurityContext securityContext) {
        SecurityTracing tracing = SecurityTracing.get();
        tracing.securityContext(securityContext);

        SecurityFilter.FilterContext filterContext = initRequestFiltering(request);

        if (filterContext.isShouldFinish()) {
            // 404
            tracing.finish();
            return;
        }

        URI requestUri = request.getUriInfo().getRequestUri();
        String query = requestUri.getQuery();
        String origRequest;
        if ((null == query) || query.isEmpty()) {
            origRequest = requestUri.getPath();
        } else {
            origRequest = requestUri.getPath() + "?" + query;
        }
        Map<String, List<String>> allHeaders = new HashMap<>(filterContext.getHeaders());
        allHeaders.put(Security.HEADER_ORIG_URI, List.of(origRequest));

        SecurityEnvironment.Builder envBuilder = SecurityEnvironment.builder(security.serverTime())
                .path(filterContext.getResourcePath())
                .targetUri(filterContext.getTargetUri())
                .method(filterContext.getMethod())
                .headers(allHeaders)
                .addAttribute("resourceType", filterContext.getResourceName());

        // The following two lines are not possible in JAX-RS or Jersey - we would have to touch
        // underlying web server's request...
        String remoteHost = (String) request.getProperty("io.helidon.jaxrs.remote-host");
        Integer remotePort = (Integer) request.getProperty("io.helidon.jaxrs.remote-port");
        if (remoteHost != null) {
            envBuilder.addAttribute("userIp", remoteHost);
        }
        if (remotePort != null) {
            envBuilder.addAttribute("userPort", remotePort);
        }

        SecurityEnvironment env = envBuilder.build();

        EndpointConfig ec = EndpointConfig.builder()
                .securityLevels(filterContext.getMethodSecurity().getSecurityLevels())
                .build();

        try {
            securityContext.env(env);
            securityContext.endpointConfig(ec);

            request.setProperty(PROP_FILTER_CONTEXT, filterContext);
            //context is needed even if authn/authz fails - for auditing
            request.setSecurityContext(new JerseySecurityContext(securityContext,
                                                                 filterContext.getMethodSecurity(),
                                                                 "https".equals(filterContext.getTargetUri().getScheme())));

            processSecurity(request, filterContext, tracing, securityContext);
        } finally {
            if (filterContext.isTraceSuccess()) {
                tracing.logProceed();
                tracing.finish();
            } else {
                tracing.logDeny();
                tracing.error("aborted");
            }
        }
    }

    protected void authenticate(SecurityFilter.FilterContext context, SecurityContext securityContext, AtnTracing atnTracing) {
        try {
            SecurityDefinition methodSecurity = context.getMethodSecurity();

            if (methodSecurity.requiresAuthentication()) {
                //authenticate request
                SecurityClientBuilder<AuthenticationResponse> clientBuilder = securityContext
                        .atnClientBuilder()
                        .optional(methodSecurity.authenticationOptional())
                        .tracingSpan(atnTracing.findParent().orElse(null));

                clientBuilder.explicitProvider(methodSecurity.getAuthenticator());
                processAuthentication(context, clientBuilder, methodSecurity, atnTracing);
            }
        } finally {
            if (context.isTraceSuccess()) {
                securityContext.user()
                        .ifPresent(atnTracing::logUser);

                securityContext.service()
                        .ifPresent(atnTracing::logService);

                atnTracing.finish();
            } else {
                Throwable ctxThrowable = context.getTraceThrowable();
                if (null == ctxThrowable) {
                    atnTracing.error(context.getTraceDescription());
                } else {
                    atnTracing.error(ctxThrowable);
                }
            }
        }
    }

    protected void processAuthentication(FilterContext context,
                                         SecurityClientBuilder<AuthenticationResponse> clientBuilder,
                                         SecurityDefinition methodSecurity, AtnTracing atnTracing) {

        AuthenticationResponse response = clientBuilder.buildAndGet();

        SecurityResponse.SecurityStatus responseStatus = response.status();

        atnTracing.logStatus(responseStatus);

        switch (responseStatus) {
        case SUCCESS:
            //everything is fine, we can continue with processing
            return;
        case FAILURE_FINISH:
            if (methodSecurity.authenticationOptional()) {
                logger().finest("Authentication failed, but was optional, so assuming anonymous");
            } else {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                context.setTraceThrowable(response.throwable().orElse(null));
                context.setShouldFinish(true);

                int status = response.statusCode().orElse(Response.Status.UNAUTHORIZED.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }

            return;
        case SUCCESS_FINISH:
            context.setShouldFinish(true);

            int status = response.statusCode().orElse(Response.Status.OK.getStatusCode());
            abortRequest(context, response, status, Map.of());

            return;
        case ABSTAIN:
            if (methodSecurity.authenticationOptional()) {
                logger().finest("Authentication failed, but was optional, so assuming anonymous");
            } else {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                context.setShouldFinish(true);
                abortRequest(context,
                             response,
                             Response.Status.UNAUTHORIZED.getStatusCode(),
                             Map.of());
            }
            return;
        case FAILURE:
            if (methodSecurity.authenticationOptional()) {
                logger().finest("Authentication failed, but was optional, so assuming anonymous");
            } else {
                context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                context.setTraceThrowable(response.throwable().orElse(null));
                context.setTraceSuccess(false);
                abortRequest(context,
                             response,
                             Response.Status.UNAUTHORIZED.getStatusCode(),
                             Map.of());
                context.setShouldFinish(true);
            }
            return;
        default:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
            context.setShouldFinish(true);
            SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
            context.setTraceThrowable(throwable);
            throw throwable;
        }
    }

    protected abstract Logger logger();

    protected void authorize(FilterContext context,
                             SecurityContext securityContext,
                             AtzTracing atzTracing) {
        if (context.getMethodSecurity().isAtzExplicit()) {
            // authorization is explicitly done by user, we MUST skip it here
            context.setExplicitAtz(true);
            return;
        }

        try {
            //now authorize (also authorize anonymous requests, as we may have a path-based authorization that allows public
            // access
            if (context.getMethodSecurity().requiresAuthorization()) {
                SecurityClientBuilder<AuthorizationResponse> clientBuilder = securityContext.atzClientBuilder()
                        .tracingSpan(atzTracing.findParent().orElse(null))
                        .explicitProvider(context.getMethodSecurity().getAuthorizer());

                processAuthorization(context, clientBuilder);
            }
        } finally {
            if (context.isTraceSuccess()) {
                atzTracing.finish();
            } else {
                Throwable throwable = context.getTraceThrowable();
                if (null == throwable) {
                    atzTracing.error(context.getTraceDescription());
                } else {
                    atzTracing.error(throwable);
                }
            }

        }
    }

    protected void processAuthorization(SecurityFilter.FilterContext context,
                                        SecurityClientBuilder<AuthorizationResponse> clientBuilder) {
        // now fully synchronous
        AuthorizationResponse response = clientBuilder.buildAndGet();
        SecurityResponse.SecurityStatus responseStatus = response.status();

        switch (responseStatus) {
        case SUCCESS:
            //everything is fine, we can continue with processing
            return;
        case FAILURE_FINISH:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.description().orElse(responseStatus.toString()));
            context.setTraceThrowable(response.throwable().orElse(null));
            context.setShouldFinish(true);
            int status = response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode());
            abortRequest(context, response, status, Map.of());
            return;
        case SUCCESS_FINISH:
            context.setShouldFinish(true);
            status = response.statusCode().orElse(Response.Status.OK.getStatusCode());
            abortRequest(context, response, status, Map.of());
            return;
        case FAILURE:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.description().orElse(responseStatus.toString()));
            context.setTraceThrowable(response.throwable().orElse(null));
            context.setShouldFinish(true);
            abortRequest(context,
                         response,
                         response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                         Map.of());
            return;
        case ABSTAIN:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.description().orElse(responseStatus.toString()));
            context.setShouldFinish(true);
            abortRequest(context,
                         response,
                         response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                         Map.of());
            return;
        default:
            context.setTraceSuccess(false);
            context.setTraceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
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

        int statusCode = response.statusCode().orElse(defaultStatusCode);
        Map<String, List<String>> responseHeaders = response.responseHeaders();

        Response.ResponseBuilder responseBuilder = Response.status(statusCode);
        if (responseHeaders.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : defaultHeaders.entrySet()) {
                responseBuilder.header(entry.getKey(), entry.getValue());
            }
        } else {
            updateHeaders(responseHeaders, responseBuilder);
        }

        if (featureConfig.isDebug()) {
            response.description().ifPresent(responseBuilder::entity);
        }

        if (featureConfig.useAbortWith()) {
            context.getJerseyRequest().abortWith(responseBuilder.build());
        } else {
            String description = response.description()
                    .orElse("Security did not allow this request to proceed.");
            throw new WebApplicationException(description, responseBuilder.build());
        }
    }

    protected void updateHeaders(Map<String, List<String>> responseHeaders, Response.ResponseBuilder responseBuilder) {
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                responseBuilder.header(entry.getKey(), value);
            }
        }
    }

    protected FilterContext configureContext(FilterContext context,
                                             ContainerRequestContext requestContext,
                                             UriInfo uriInfo) {
        context.setMethod(requestContext.getMethod());
        context.setHeaders(requestContext.getHeaders());
        context.setTargetUri(requestContext.getUriInfo().getRequestUri());
        context.setResourcePath(context.getTargetUri().getPath());

        context.setJerseyRequest((ContainerRequest) requestContext);

        // now extract headers
        featureConfig().getQueryParamHandlers()
                .forEach(handler -> handler.extract(uriInfo, context.getHeaders()));

        return context;
    }

    protected Security security() {
        return security;
    }

    protected FeatureConfig featureConfig() {
        return featureConfig;
    }

    protected abstract void processSecurity(ContainerRequestContext request,
                                            FilterContext context,
                                            SecurityTracing tracing,
                                            SecurityContext securityContext);

    protected abstract SecurityFilter.FilterContext initRequestFiltering(ContainerRequestContext requestContext);

    static class FilterContext {
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

    Config config(String child) {
        return security.configFor(child);
    }
}

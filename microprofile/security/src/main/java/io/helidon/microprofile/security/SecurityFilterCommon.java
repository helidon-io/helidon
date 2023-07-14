/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.security;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.uri.UriQuery;
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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;

/**
 * Helper class for security filters.
 */
abstract class SecurityFilterCommon {
    static final String PROP_FILTER_CONTEXT = "io.helidon.security.jersey.FilterContext";

    private static final List<SecurityResponseMapper> RESPONSE_MAPPERS = HelidonServiceLoader
            .builder(ServiceLoader.load(SecurityResponseMapper.class)).build().asList();

    private final Security security;

    private final FeatureConfig featureConfig;

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilterCommon(@Context Security security, @Context FeatureConfig featureConfig) {
        this.security = security;
        this.featureConfig = featureConfig;
    }

    protected void doFilter(ContainerRequestContext request, SecurityContext securityContext) {
        SecurityTracing tracing = SecurityTracing.get();
        tracing.securityContext(securityContext);

        SecurityFilterContext filterContext = initRequestFiltering(request);

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
                .queryParams(filterContext.getQueryParams())
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

    protected void authenticate(SecurityFilterContext context, SecurityContext securityContext, AtnTracing atnTracing) {
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

    protected void processAuthentication(SecurityFilterContext context,
                                         SecurityClientBuilder<AuthenticationResponse> clientBuilder,
                                         SecurityDefinition methodSecurity, AtnTracing atnTracing) {

        AuthenticationResponse response = clientBuilder.submit();

        SecurityResponse.SecurityStatus responseStatus = response.status();

        atnTracing.logStatus(responseStatus);

        switch (responseStatus) {
            case SUCCESS -> {
                //everything is fine, we can continue with processing
            }
            case FAILURE_FINISH -> {
                if (methodSecurity.authenticationOptional()) {
                    logger().log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
                } else {
                    context.setTraceSuccess(false);
                    context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                    context.setTraceThrowable(response.throwable().orElse(null));
                    context.setShouldFinish(true);

                    int status = response.statusCode().orElse(Response.Status.UNAUTHORIZED.getStatusCode());
                    abortRequest(context, response, status, Map.of());
                }
            }
            case SUCCESS_FINISH -> {
                context.setShouldFinish(true);
                int status = response.statusCode().orElse(Response.Status.OK.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }
            case ABSTAIN -> {
                if (methodSecurity.authenticationOptional()) {
                    logger().log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
                } else {
                    context.setTraceSuccess(false);
                    context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                    context.setShouldFinish(true);
                    abortRequest(context,
                            response,
                            Response.Status.UNAUTHORIZED.getStatusCode(),
                            Map.of());
                }
            }
            case FAILURE -> {
                if (methodSecurity.authenticationOptional() && !methodSecurity.failOnFailureIfOptional()) {
                    logger().log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
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
            }
            //noinspection DuplicatedCode
            default -> {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
                context.setShouldFinish(true);
                SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
                context.setTraceThrowable(throwable);
                throw throwable;
            }
        }
    }

    protected abstract System.Logger logger();

    protected void authorize(SecurityFilterContext context,
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

    protected void processAuthorization(SecurityFilterContext context,
                                        SecurityClientBuilder<AuthorizationResponse> clientBuilder) {
        // now fully synchronous
        AuthorizationResponse response = clientBuilder.submit();
        SecurityResponse.SecurityStatus responseStatus = response.status();

        switch (responseStatus) {
            case SUCCESS -> {
                //everything is fine, we can continue with processing
            }
            case FAILURE_FINISH -> {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                context.setTraceThrowable(response.throwable().orElse(null));
                context.setShouldFinish(true);
                int status = response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }
            case SUCCESS_FINISH -> {
                context.setShouldFinish(true);
                int status = response.statusCode().orElse(Response.Status.OK.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }
            case FAILURE -> {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                context.setTraceThrowable(response.throwable().orElse(null));
                context.setShouldFinish(true);
                abortRequest(context,
                        response,
                        response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                        Map.of());
            }
            case ABSTAIN -> {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse(responseStatus.toString()));
                context.setShouldFinish(true);
                abortRequest(context,
                        response,
                        response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                        Map.of());
            }
            //noinspection DuplicatedCode
            default -> {
                context.setTraceSuccess(false);
                context.setTraceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
                context.setShouldFinish(true);
                SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
                context.setTraceThrowable(throwable);
                throw throwable;
            }
        }
    }

    protected void abortRequest(SecurityFilterContext context,
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

        // Run security response mappers if available, or revert to old logic for compatibility
        if (!RESPONSE_MAPPERS.isEmpty()) {
            RESPONSE_MAPPERS.forEach(m -> m.aborted(response, responseBuilder));
        } else if (featureConfig.isDebug()) {
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

    protected SecurityFilterContext configureContext(SecurityFilterContext context,
                                                     ContainerRequestContext requestContext,
                                                     UriInfo uriInfo) {
        context.setMethod(requestContext.getMethod());
        context.setHeaders(requestContext.getHeaders());
        context.setTargetUri(requestContext.getUriInfo().getRequestUri());
        context.setResourcePath(context.getTargetUri().getPath());
        context.setQueryParams(UriQuery.create(uriInfo.getRequestUri()));

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
                                            SecurityFilterContext context,
                                            SecurityTracing tracing,
                                            SecurityContext securityContext);

    protected abstract SecurityFilterContext initRequestFiltering(ContainerRequestContext requestContext);

    Config config(String child) {
        return security.configFor(child);
    }
}

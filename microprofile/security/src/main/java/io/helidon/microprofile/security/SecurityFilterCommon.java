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
package io.helidon.microprofile.security;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.mp.MpConfig;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.microprofile.security.spi.SecurityResponseMapper;
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
import io.helidon.webserver.security.SecurityHttpFeature;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.server.ContainerRequest;

/**
 * Helper class for security filters.
 */
abstract class SecurityFilterCommon {
    static final String PROP_FILTER_CONTEXT = "io.helidon.security.jersey.FilterContext";

    private static final List<SecurityResponseMapper> RESPONSE_MAPPERS = HelidonServiceLoader
            .builder(ServiceLoader.load(SecurityResponseMapper.class)).build().asList();
    private static final LazyValue<List<PathConfig>> PATH_CONFIGS = LazyValue.create(SecurityFilterCommon::createPathConfigs);

    private final Security security;

    private final FeatureConfig featureConfig;

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilterCommon(@Context Security security, @Context FeatureConfig featureConfig) {
        this.security = security;
        this.featureConfig = featureConfig;
    }

    private static List<PathConfig> createPathConfigs() {
        return MpConfig.toHelidonConfig(ConfigProvider.getConfig())
                .get("server.features.security.endpoints")
                .asNodeList()
                .orElse(List.of())
                .stream()
                .map(PathConfig::create)
                .toList();
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    static Class<?> getRealClass(Class<?> object) {
        Class<?> result = object;
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    protected void doFilter(ContainerRequestContext request, SecurityContext securityContext) {
        SecurityTracing tracing = SecurityTracing.get();
        tracing.securityContext(securityContext);

        SecurityFilterContext filterContext = initRequestFiltering(request);

        if (logger().isLoggable(Level.TRACE)) {
            logger().log(Level.TRACE, "Endpoint {0} security context: {1}",
                         request.getUriInfo().getRequestUri(),
                         filterContext);
        }

        if (filterContext.shouldFinish()) {
            if (logger().isLoggable(Level.TRACE)) {
                logger().log(Level.TRACE, "Endpoint %s not found, no security", request.getUriInfo().getRequestUri());
            }
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
        Map<String, List<String>> allHeaders = new HashMap<>(filterContext.headers());
        allHeaders.put(Security.HEADER_ORIG_URI, List.of(origRequest));

        SecurityEnvironment.Builder envBuilder = SecurityEnvironment.builder(security.serverTime())
                .transport(requestUri.getScheme())
                .path(filterContext.resourcePath())
                .targetUri(filterContext.targetUri())
                .method(filterContext.method())
                .queryParams(filterContext.queryParams())
                .headers(allHeaders)
                .addAttribute("resourceType", filterContext.resourceName());

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
        Map<String, Config> configMap = new HashMap<>();
        findMethodConfig(UriPath.create(requestUri.getPath()))
                .asNode()
                .ifPresent(conf -> conf.asNodeList().get().forEach(node -> configMap.put(node.name(), node)));

        EndpointConfig ec = EndpointConfig.builder()
                .securityLevels(filterContext.methodSecurity().securityLevels())
                .configMap(configMap)
                .build();

        try {
            securityContext.env(env);
            securityContext.endpointConfig(ec);

            request.setProperty(PROP_FILTER_CONTEXT, filterContext);
            //context is needed even if authn/authz fails - for auditing
            request.setSecurityContext(new JerseySecurityContext(securityContext,
                                                                 filterContext.methodSecurity(),
                                                                 "https".equals(filterContext.targetUri().getScheme())));

            processSecurity(request, filterContext, tracing, securityContext);
        } finally {
            if (filterContext.traceSuccess()) {
                tracing.logProceed();
                tracing.finish();
            } else {
                tracing.logDeny();
                tracing.error("aborted");
            }
        }
    }

    Config findMethodConfig(UriPath path) {
        return PATH_CONFIGS.get()
                .stream()
                .filter(pathConfig -> pathConfig.pathMatcher.prefixMatch(path).accepted())
                .findFirst()
                .map(PathConfig::config)
                .orElseGet(Config::empty);
    }

    protected void authenticate(SecurityFilterContext context, SecurityContext securityContext, AtnTracing atnTracing) {
        try {
            SecurityDefinition methodSecurity = context.methodSecurity();

            if (methodSecurity.requiresAuthentication()) {
                if (logger().isLoggable(Level.TRACE)) {
                    logger().log(Level.TRACE, "Endpoint {0} requires authentication", context.targetUri());
                }
                //authenticate request
                SecurityClientBuilder<AuthenticationResponse> clientBuilder = securityContext
                        .atnClientBuilder()
                        .optional(methodSecurity.authenticationOptional())
                        .tracingSpan(atnTracing.findParent().orElse(null));

                clientBuilder.explicitProvider(methodSecurity.authenticator());
                processAuthentication(context, clientBuilder, methodSecurity, atnTracing);
            } else {
                if (logger().isLoggable(Level.TRACE)) {
                    logger().log(Level.TRACE, "Endpoint {0} does not require authentication", context.targetUri());
                }
            }
        } finally {
            if (context.traceSuccess()) {
                securityContext.user()
                        .ifPresent(atnTracing::logUser);

                securityContext.service()
                        .ifPresent(atnTracing::logService);

                atnTracing.finish();
            } else {
                Throwable ctxThrowable = context.traceThrowable();
                if (null == ctxThrowable) {
                    atnTracing.error(context.traceDescription());
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
                io.helidon.common.context.Context helidonContext = Contexts.context()
                        .orElseThrow(() -> new IllegalStateException("Context must be available in Jersey"));
                helidonContext.register(SecurityHttpFeature.CONTEXT_RESPONSE_HEADERS, response.responseHeaders());
            }
            case FAILURE_FINISH -> {
                if (methodSecurity.authenticationOptional()) {
                    logger().log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
                } else {
                    context.traceSuccess(false);
                    context.traceDescription(response.description().orElse(responseStatus.toString()));
                    context.traceThrowable(response.throwable().orElse(null));
                    context.shouldFinish(true);

                    int status = response.statusCode().orElse(Response.Status.UNAUTHORIZED.getStatusCode());
                    abortRequest(context, response, status, Map.of());
                }
            }
            case SUCCESS_FINISH -> {
                context.shouldFinish(true);
                int status = response.statusCode().orElse(Response.Status.OK.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }
            case ABSTAIN -> {
                if (methodSecurity.authenticationOptional()) {
                    logger().log(Level.TRACE, "Authentication failed, but was optional, so assuming anonymous");
                } else {
                    context.traceSuccess(false);
                    context.traceDescription(response.description().orElse(responseStatus.toString()));
                    context.shouldFinish(true);
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
                    context.traceDescription(response.description().orElse(responseStatus.toString()));
                    context.traceThrowable(response.throwable().orElse(null));
                    context.traceSuccess(false);
                    abortRequest(context,
                            response,
                            Response.Status.UNAUTHORIZED.getStatusCode(),
                            Map.of());
                    context.shouldFinish(true);
                }
            }
            //noinspection DuplicatedCode
            default -> {
                context.traceSuccess(false);
                context.traceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
                context.shouldFinish(true);
                SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
                context.traceThrowable(throwable);
                throw throwable;
            }
        }
    }

    protected abstract System.Logger logger();

    protected void authorize(SecurityFilterContext context,
                             SecurityContext securityContext,
                             AtzTracing atzTracing) {
        if (context.methodSecurity().atzExplicit()) {
            // authorization is explicitly done by user, we MUST skip it here
            if (logger().isLoggable(Level.TRACE)) {
                logger().log(Level.TRACE, "Endpoint {0} uses explicit authorization, skipping", context.targetUri());
            }
            context.explicitAtz(true);
            return;
        }

        try {
            //now authorize (also authorize anonymous requests, as we may have a path-based authorization that allows public
            // access
            if (context.methodSecurity().requiresAuthorization()) {
                if (logger().isLoggable(Level.TRACE)) {
                    logger().log(Level.TRACE, "Endpoint {0} requires authorization", context.targetUri());
                }
                SecurityClientBuilder<AuthorizationResponse> clientBuilder = securityContext.atzClientBuilder()
                        .tracingSpan(atzTracing.findParent().orElse(null))
                        .explicitProvider(context.methodSecurity().authorizer());

                processAuthorization(context, clientBuilder);
            } else {
                if (logger().isLoggable(Level.TRACE)) {
                    logger().log(Level.TRACE, "Endpoint {0} does not require authorization. Method security: {1}",
                                 context.targetUri(),
                                 context.methodSecurity());
                }
            }
        } finally {
            if (context.traceSuccess()) {
                atzTracing.finish();
            } else {
                Throwable throwable = context.traceThrowable();
                if (null == throwable) {
                    atzTracing.error(context.traceDescription());
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
                context.traceSuccess(false);
                context.traceDescription(response.description().orElse(responseStatus.toString()));
                context.traceThrowable(response.throwable().orElse(null));
                context.shouldFinish(true);
                int status = response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }
            case SUCCESS_FINISH -> {
                context.shouldFinish(true);
                int status = response.statusCode().orElse(Response.Status.OK.getStatusCode());
                abortRequest(context, response, status, Map.of());
            }
            case FAILURE -> {
                context.traceSuccess(false);
                context.traceDescription(response.description().orElse(responseStatus.toString()));
                context.traceThrowable(response.throwable().orElse(null));
                context.shouldFinish(true);
                abortRequest(context,
                        response,
                        response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                        Map.of());
            }
            case ABSTAIN -> {
                context.traceSuccess(false);
                context.traceDescription(response.description().orElse(responseStatus.toString()));
                context.shouldFinish(true);
                abortRequest(context,
                        response,
                        response.statusCode().orElse(Response.Status.FORBIDDEN.getStatusCode()),
                        Map.of());
            }
            //noinspection DuplicatedCode
            default -> {
                context.traceSuccess(false);
                context.traceDescription(response.description().orElse("UNKNOWN_RESPONSE: " + responseStatus));
                context.shouldFinish(true);
                SecurityException throwable = new SecurityException("Invalid SecurityStatus returned: " + responseStatus);
                context.traceThrowable(throwable);
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
            context.jerseyRequest().abortWith(responseBuilder.build());
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
        context.method(requestContext.getMethod());
        context.headers(requestContext.getHeaders());
        context.targetUri(requestContext.getUriInfo().getRequestUri());
        context.resourcePath(context.targetUri().getPath());
        context.queryParams(UriQuery.create(uriInfo.getRequestUri()));

        context.jerseyRequest((ContainerRequest) requestContext);

        // now extract headers
        featureConfig().getQueryParamHandlers()
                .forEach(handler -> handler.extract(uriInfo, context.headers()));

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

    private record PathConfig(PathMatcher pathMatcher, Config config) {

        static PathConfig create(Config config) {
            String path = config.get("path").asString().orElseThrow();
            PathMatcher matcher = PathMatchers.create(path);
            return new PathConfig(matcher, config.get("config"));
        }

    }
}

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.UriInfo;

import io.helidon.security.SecurityContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ContainerRequest;

/**
 * Security context must be created before resource method is matched, so it is available for injection into resource classes.
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
class SecurityPreMatchingFilter extends SecurityFilterCommon implements ContainerRequestFilter {
    private static final Logger LOGGER = Logger.getLogger(SecurityPreMatchingFilter.class.getName());

    static final String PROP_CLOSE_PARENT_SPAN = "io.helidon.security.jersey.SecurityFilter.closeParent";
    static final String PROP_PARENT_SPAN = "io.helidon.security.jersey.SecurityFilter.parentSpan";

    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger();


    @Context
    private InjectionManager injectionManager;

    @Context
    private Provider<SpanContext> parentSpanContextProvider;

    @Context
    private ExecutorService executorService;

    @Context
    private UriInfo uriInfo;



    @Override
    public void filter(ContainerRequestContext request) {
        boolean closeParentSpan = false;
        SpanContext requestSpanContext = parentSpanContextProvider.get();

        if (null == requestSpanContext) {
            closeParentSpan = true;
            Span requestSpan = security().getTracer().buildSpan("security-parent").start();
            request.setProperty(PROP_PARENT_SPAN, requestSpan);
            requestSpanContext = requestSpan.context();
        }

        request.setProperty(PROP_CLOSE_PARENT_SPAN, closeParentSpan);

        // create a new security context
        SecurityContext securityContext = security()
                .contextBuilder(Integer.toString(CONTEXT_COUNTER.incrementAndGet(), Character.MAX_RADIX))
                .tracingSpan(requestSpanContext)
                .executorService(executorService)
                .build();

        injectionManager.<Ref<SecurityContext>>getInstance((new GenericType<Ref<SecurityContext>>() { }).getType())
                .set(securityContext);

        if (featureConfig().shouldUsePrematchingAuthentication()) {
            doFilter(request, securityContext);
        }
    }

    @Override
    protected void processSecurity(ContainerRequestContext request,
                                   SecurityFilter.FilterContext filterContext,
                                   Span securitySpan,
                                   SecurityContext securityContext) {

        // when I reach this point, I am sure we should at least authenticate in prematching filter
        authenticate(filterContext, securitySpan, securityContext);

        LOGGER.finest(() -> "Filter after authentication. Should finish: " + filterContext.isShouldFinish());

        // authentication failed
        if (filterContext.isShouldFinish()) {
            return;
        }

        filterContext.clearTrace();

        if (featureConfig().shouldUsePrematchingAuthorization()) {
            LOGGER.finest(() -> "Using pre-matching authorization");
            authorize(filterContext, securitySpan, securityContext);
        }

        LOGGER.finest(() -> "Filter completed (after authorization)");
    }

    @Override
    protected SecurityFilter.FilterContext initRequestFiltering(ContainerRequestContext requestContext) {
        SecurityFilter.FilterContext context = new SecurityFilter.FilterContext();

        // this is a pre-matching filter, so no method or class security

        SecurityDefinition methodDef = new SecurityDefinition(false);
        methodDef.requiresAuthentication(true);
        methodDef.setRequiresAuthorization(featureConfig().shouldUsePrematchingAuthorization());
        context.setMethodSecurity(methodDef);
        context.setResourceName("jax-rs");
        context.setMethod(requestContext.getMethod());
        context.setHeaders(HttpUtil.toSimpleMap(requestContext.getHeaders()));
        context.setTargetUri(requestContext.getUriInfo().getRequestUri());
        context.setResourcePath(context.getTargetUri().getPath());

        context.setJerseyRequest((ContainerRequest) requestContext);

        // now extract headers
        featureConfig().getQueryParamHandlers()
                .forEach(handler -> handler.extract(uriInfo, context.getHeaders()));

        return context;
    }

    @Override
    protected Logger logger() {
        return LOGGER;
    }
}

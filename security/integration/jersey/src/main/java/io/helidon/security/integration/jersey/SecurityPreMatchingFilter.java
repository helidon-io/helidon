/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.UriInfo;

import io.helidon.common.context.Contexts;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.common.SecurityTracing;

import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.collection.Ref;

/**
 * Security context must be created before resource method is matched, so it is available for injection into resource classes.
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
class SecurityPreMatchingFilter extends SecurityFilterCommon implements ContainerRequestFilter {
    private static final Logger LOGGER = Logger.getLogger(SecurityPreMatchingFilter.class.getName());

    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger();

    @Context
    private InjectionManager injectionManager;

    @Context
    private UriInfo uriInfo;

    @Override
    public void filter(ContainerRequestContext request) {
        SecurityTracing tracing = SecurityTracing.get();

        // create a new security context
        SecurityContext securityContext = security()
                .contextBuilder(Integer.toString(CONTEXT_COUNTER.incrementAndGet(), Character.MAX_RADIX))
                .tracingSpan(tracing.findParent().orElse(null))
                .build();

        Contexts.context().ifPresent(ctx -> ctx.register(securityContext));

        injectionManager.<Ref<SecurityContext>>getInstance((new GenericType<Ref<SecurityContext>>() { }).getType())
                .set(securityContext);

        if (featureConfig().shouldUsePrematchingAuthentication()) {
            doFilter(request, securityContext);
        }
    }

    @Override
    protected void processSecurity(ContainerRequestContext request,
                                   FilterContext filterContext,
                                   SecurityTracing tracing,
                                   SecurityContext securityContext) {

        // when I reach this point, I am sure we should at least authenticate in prematching filter
        authenticate(filterContext, securityContext, tracing.atnTracing());

        LOGGER.finest(() -> "Filter after authentication. Should finish: " + filterContext.isShouldFinish());

        // authentication failed
        if (filterContext.isShouldFinish()) {
            return;
        }

        filterContext.clearTrace();

        if (featureConfig().shouldUsePrematchingAuthorization()) {
            LOGGER.finest(() -> "Using pre-matching authorization");
            authorize(filterContext, securityContext, tracing.atzTracing());
        }

        LOGGER.finest(() -> "Filter completed (after authorization)");
    }

    @Override
    protected SecurityFilter.FilterContext initRequestFiltering(ContainerRequestContext requestContext) {
        SecurityFilter.FilterContext context = new SecurityFilter.FilterContext();

        // this is a pre-matching filter, so no method or class security

        SecurityDefinition methodDef = new SecurityDefinition(false, false);
        methodDef.requiresAuthentication(true);
        methodDef.setRequiresAuthorization(featureConfig().shouldUsePrematchingAuthorization());
        context.setMethodSecurity(methodDef);
        context.setResourceName("jax-rs");

        return configureContext(context, requestContext, uriInfo);
    }

    @Override
    protected Logger logger() {
        return LOGGER;
    }
}

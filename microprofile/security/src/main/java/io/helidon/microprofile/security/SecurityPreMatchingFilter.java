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
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Contexts;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.common.SecurityTracing;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.collection.Ref;

/**
 * Security context must be created before resource method is matched, so it is available for injection into resource classes.
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
class SecurityPreMatchingFilter extends SecurityFilterCommon implements ContainerRequestFilter {
    private static final System.Logger LOGGER = System.getLogger(SecurityPreMatchingFilter.class.getName());

    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger();

    private final InjectionManager injectionManager;
    private final UriInfo uriInfo;

    SecurityPreMatchingFilter(@Context Security security,
                              @Context FeatureConfig featureConfig,
                              @Context InjectionManager injectionManager,
                              @Context UriInfo uriInfo) {
        super(security, featureConfig);

        this.injectionManager = injectionManager;
        this.uriInfo = uriInfo;
    }

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
                                   SecurityFilterContext filterContext,
                                   SecurityTracing tracing,
                                   SecurityContext securityContext) {

        // when I reach this point, I am sure we should at least authenticate in prematching filter
        authenticate(filterContext, securityContext, tracing.atnTracing());

        LOGGER.log(Level.TRACE, () -> "Filter after authentication. Should finish: " + filterContext.isShouldFinish());

        // authentication failed
        if (filterContext.isShouldFinish()) {
            return;
        }

        filterContext.clearTrace();

        if (featureConfig().shouldUsePrematchingAuthorization()) {
            LOGGER.log(Level.TRACE, () -> "Using pre-matching authorization");
            authorize(filterContext, securityContext, tracing.atzTracing());
        }

        LOGGER.log(Level.TRACE, () -> "Filter completed (after authorization)");
    }

    @Override
    protected SecurityFilterContext initRequestFiltering(ContainerRequestContext requestContext) {
        SecurityFilterContext context = new SecurityFilterContext();

        // this is a pre-matching filter, so no method or class security

        SecurityDefinition methodDef = new SecurityDefinition(false, false);
        methodDef.requiresAuthentication(true);
        methodDef.setRequiresAuthorization(featureConfig().shouldUsePrematchingAuthorization());
        context.setMethodSecurity(methodDef);
        context.setResourceName("jax-rs");

        return configureContext(context, requestContext, uriInfo);
    }

    @Override
    protected System.Logger logger() {
        return LOGGER;
    }
}

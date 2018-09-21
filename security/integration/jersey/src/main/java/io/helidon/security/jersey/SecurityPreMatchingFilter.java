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

import io.helidon.security.SecurityContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.collection.Ref;

/**
 * Security context must be created before resource method is matched, so it is available for injection into resource classes.
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
class SecurityPreMatchingFilter extends SecurityFilterCommon implements ContainerRequestFilter {
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
    private FeatureConfig featureConfig;

    @Override
    public void filter(ContainerRequestContext request) {
        boolean closeParentSpan = false;
        SpanContext requestSpanContext = parentSpanContextProvider.get();

        if (null == requestSpanContext) {
            closeParentSpan = true;
            Span requestSpan = security.getTracer().buildSpan("security-parent").start();
            request.setProperty(PROP_PARENT_SPAN, requestSpan);
            requestSpanContext = requestSpan.context();
        }

        request.setProperty(PROP_CLOSE_PARENT_SPAN, closeParentSpan);

        // create a new security context
        SecurityContext securityContext = security
                .contextBuilder(Integer.toString(CONTEXT_COUNTER.incrementAndGet(), Character.MAX_RADIX))
                .tracingSpan(requestSpanContext)
                .executorService(executorService)
                .build();

        injectionManager.<Ref<SecurityContext>>getInstance((new GenericType<Ref<SecurityContext>>() { }).getType())
                .set(securityContext);

        if (featureConfig.shouldUsePrematching()) {
            handleSecurity(request, securityContext);
        }
    }

    private void handleSecurity(ContainerRequestContext request, SecurityContext securityContext) {
        Span securitySpan = startSecuritySpan(securityContext);
        FilterCon
    }
}

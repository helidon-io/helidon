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
package io.helidon.tracing.microprofile;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;

import io.helidon.tracing.jersey.AbstractTracingFilter;

import io.opentracing.Tracer;

/**
 * Adds tracing of Jersey calls using a post-matching filter.
 *  Microprofile Opentracing implementation.
 */
@ConstrainedTo(RuntimeType.SERVER)
@Priority(Integer.MIN_VALUE + 5)
@ApplicationScoped
public class MpTracingFilter extends AbstractTracingFilter  {
    private MpTracingHelper utils;

    /**
     * Post construct method, initialization procedures.
     */
    @PostConstruct
    public void postConstruct() {
        this.utils = MpTracingHelper.create();
    }

    @Override
    protected boolean tracingEnabled(ContainerRequestContext context) {
        // first let us find if we should trace or not
        // Optional<Traced> traced = findTraced(context);
        Optional<Object> traced = Optional.empty();

        if (traced.isPresent()) {
            // this is handled by CDI extension for annotated resources
            return false;
        }
        return utils.tracingEnabled();
    }

    @Override
    protected String spanName(ContainerRequestContext context) {
        return utils.operationName(context);
    }

    @Override
    protected void configureSpan(Tracer.SpanBuilder spanBuilder) {

    }

//    private Optional<Traced> findTraced(ContainerRequestContext requestContext) {
//        // TODO all annotated by "Traced" must be handled by CDI extension
//        return Optional.empty();
//    }
}

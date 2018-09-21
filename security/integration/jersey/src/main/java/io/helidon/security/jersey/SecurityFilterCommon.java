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

import javax.ws.rs.core.Context;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Helper class for security filters.
 */
abstract class SecurityFilterCommon {
    @Context
    protected Security security;

    public SecurityFilterCommon() {
    }

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilterCommon(Security security) {
        this.security = security;
    }

    protected Span startSecuritySpan(SecurityContext securityContext) {
        Span securitySpan = startNewSpan(securityContext.getTracingSpan(), "security");
        securitySpan.log(CollectionsHelper.mapOf("securityId", securityContext.getId()));
        return securitySpan;
    }

    private Span startNewSpan(SpanContext parentSpan, String name) {
        Tracer.SpanBuilder spanBuilder = security.getTracer().buildSpan(name);
        spanBuilder.asChildOf(parentSpan);

        return spanBuilder.start();
    }
}

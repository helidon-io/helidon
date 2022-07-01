/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
package io.helidon.tracing.jersey;

import io.helidon.tracing.Span;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Adds tracing of (overall) Jersey calls.
 */
@Priority(Integer.MIN_VALUE + 10)
public class TracingFilter extends AbstractTracingFilter {
    private final TracingHelper helper = TracingHelper.create();

    @Override
    protected void configureSpan(Span.Builder spanBuilder) {
        // nothing to do with the span, just return
    }

    @Override
    protected boolean tracingEnabled(ContainerRequestContext context) {
        return true;
    }

    @Override
    protected String spanName(ContainerRequestContext context) {
        return helper.generateSpanName(context);
    }
}

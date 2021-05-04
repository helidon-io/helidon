/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.security.integration.common;

import java.util.Optional;

import io.helidon.tracing.config.SpanTracingConfig;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * Tracing of role mapping.
 */
public class RoleMapTracing extends CommonTracing {
    RoleMapTracing(Optional<SpanContext> parentSpanContext,
                   Optional<Span> parentSpan,
                   Optional<Span> securitySpan,
                   Optional<Span> span,
                   SpanTracingConfig spanConfig) {
        super(parentSpanContext, parentSpan, securitySpan, span, spanConfig);
    }
}

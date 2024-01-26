/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.grpc.core;

import java.util.Optional;

import io.grpc.Context;
import io.helidon.tracing.Span;

/**
 * Contextual information related to Tracing.
 */
public final class GrpcTracingContext {
    private static final String SPAN_KEY_NAME = "io.helidon.tracing.active-span";

    /**
     * Context key for Span instance.
     */
    public static final Context.Key<Span> SPAN_KEY = Context.key(SPAN_KEY_NAME);

    /**
     * Get the current active span associated with the context.
     *
     * @return span if one is in current context
     */
    public static Optional<Span> activeSpan() {
        return Optional.ofNullable(SPAN_KEY.get());
    }

    private GrpcTracingContext() {
    }
}

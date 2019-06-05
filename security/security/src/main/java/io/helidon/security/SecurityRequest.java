/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * Common methods for all security requests (authentication, authorization, and identity propagation).
 */
public interface SecurityRequest {
    /**
     * If true and security provider does not find required information in request/response/message, or headers, it should
     * return {@link SecurityResponse.SecurityStatus#FAILURE} without doing any side effects (e.g. should not redirect, call third
     * parties etc.).
     *
     * @return true if security is optional
     */
    default boolean isOptional() {
        return false;
    }

    /**
     * Access request message entity.
     *
     * @return Entity of the request, if current request has entity
     * @deprecated Access to entity is to be removed from security
     */
    @Deprecated
    default Optional<Entity> requestEntity() {
        return Optional.empty();
    }

    /**
     * Access response message entity.
     *
     * @return Entity of the response, if current response can have entity
     * @deprecated Access to entity is to be removed from security
     */
    @Deprecated
    default Optional<Entity> responseEntity() {
        return Optional.empty();
    }

    /**
     * Get the span to trace subsequent requests.
     *
     * @return Open tracing Span instance (started) of the parent of the current request, never null.
     * @see io.opentracing.util.GlobalTracer#get()
     * @see io.opentracing.Tracer#buildSpan(String)
     * @deprecated use {@link #tracingSpanContext()} instead
     */
    @Deprecated
    Span tracingSpan();

    /**
     * Parent span for tracing. There may be no parent defined (such as when tracing is disabled).
     *
     * @return tracing span context if available
     */
    Optional<SpanContext> tracingSpanContext();

    /**
     * Return a map of keys to resource instances.
     * By default the mapping is under "object".
     *
     * @return a map of object keys to object instances
     */
    Map<String, Supplier<Object>> resources();

}

/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.security.spi.SecurityProvider;

import io.opentracing.SpanContext;

/**
 * Fluent API to build a security request.
 *
 * @param <T> Type of the builder, used to enable extensibility of this builder
 */
public class SecurityRequestBuilder<T extends SecurityRequestBuilder<T>> {
    private final T myInstance;
    private final SecurityContext context;
    private final Map<String, Supplier<Object>> resources = new HashMap<>();
    private String providerName;
    private boolean isOptional;
    private SpanContext tracingSpanContext;

    @SuppressWarnings("unchecked")
    SecurityRequestBuilder(SecurityContext context) {
        this.myInstance = (T) this;
        this.context = context;
    }

    /**
     * Put object to this request. Creates a default mapping to name "object".
     *
     * @param resource resource instance to be available to security provider
     * @return updated builder instance
     */
    public T object(Object resource) {
        this.resources.put("object", () -> resource);
        return myInstance;
    }

    /**
     * Put object supplier to this request. Creates a default mapping to name "object".
     *
     * @param resource supplier of resource instance to be available to security provider
     * @return updated builder instance
     */
    public T object(Supplier<Object> resource) {
        this.resources.put("object", resource);
        return myInstance;
    }

    /**
     * Bind object to a specific name.
     * Example: a security policy for move operation expects source object bound to "object" and target object bound to "target"
     * you could achieve this by calling
     * {@link #object(Object) object(sourceInstance)} to bind the "object" part of the statement and
     * {@link #object(String, Object) object("target", targetInstance)} to bind the "target" part of the statement
     *
     * @param key    key to bind this object under
     * @param object resource instance
     * @return updated builder instance
     */
    public T object(String key, Object object) {
        this.resources.put(key, () -> object);
        return myInstance;
    }

    /**
     * Bind object supplier to a specific name.
     * Example: a security policy for move operation expects source object bound to "object" and target object bound to "target"
     * you could achieve this by calling
     * {@link #object(Object) object(sourceInstance)} to bind the "object" part of the statement and
     * {@link #object(String, Object) object("target", targetInstance)} to bind the "target" part of the statement
     *
     * @param key    key to bind this object under
     * @param object supplier of resource instance
     * @return updated builder instance
     */
    public T object(String key, Supplier<Object> object) {
        this.resources.put(key, object);
        return myInstance;
    }

    /**
     * Tracing span to support Open tracing. Provider developer can add additional spans as children of this span
     * to trace their progress.
     *
     * @param spanContext span of current security request (e.g. authentication, authorization or outbound, or any parent if
     *                    these are not traced)
     * @return updated builder instance
     * @see io.opentracing.util.GlobalTracer#get()
     * @see io.opentracing.Tracer#buildSpan(String)
     */
    public T tracingSpan(SpanContext spanContext) {
        this.tracingSpanContext = spanContext;
        return myInstance;
    }

    /**
     * Use an explicit provider.
     *
     * @param providerName Name of a configured  {@link SecurityProvider}
     * @return updated request builder instance
     */
    public T explicitProvider(String providerName) {
        this.providerName = providerName;
        return myInstance;
    }

    /**
     * Set that security is optional. Required is default.
     *
     * @param optional set to true to make this security request optional
     * @return this instance
     */
    public T optional(boolean optional) {
        this.isOptional = optional;
        return myInstance;
    }

    /**
     * Build the security request. Not using name "build" on purpose, so overriding classes can build their expected
     * type.
     *
     * @return Security request built from this builder.
     */
    public SecurityRequest buildRequest() {
        return new SecurityRequestImpl(this);
    }

    String providerName() {
        return providerName;
    }

    private static final class SecurityRequestImpl implements SecurityRequest {
        private final String providerName;
        private final boolean isOptional;
        private final Optional<SpanContext> tracingSpanContext;
        private final Map<String, Supplier<Object>> resources = new HashMap<>();

        private SecurityRequestImpl(SecurityRequestBuilder<?> builder) {
            this.providerName = builder.providerName;
            this.isOptional = builder.isOptional;
            this.tracingSpanContext = Optional.ofNullable(builder.tracingSpanContext);
            this.resources.putAll(builder.resources);
        }

        @Override
        public boolean isOptional() {
            return isOptional;
        }

        @Override
        public Optional<SpanContext> tracingSpanContext() {
            return tracingSpanContext;
        }

        public Map<String, Supplier<Object>> resources() {
            return Collections.unmodifiableMap(resources);
        }

        @Override
        public String toString() {
            return "SecurityRequestImpl{"
                    + "providerName='" + providerName + '\''
                    + ", isOptional=" + isOptional
                    + '}';
        }
    }
}

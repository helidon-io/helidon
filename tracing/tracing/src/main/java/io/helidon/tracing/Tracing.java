/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Interception;

/**
 * Container for Helidon Declarative Tracing types.
 */
public final class Tracing {
    private Tracing() {
    }

    /**
     * Marks all service methods on this type as traced, or marks a method as traced (i.e. a new span is created for each
     * invocation of the method).
     * <p>
     * A service method is a non-private method on a {@link io.helidon.service.registry.ServiceRegistry} service.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Interception.Intercepted
    public @interface Traced {
        /**
         * Name of the span, inferred from method name by default.
         * The value can use a template {@code %1$s} for the class name, and {@code %2$s} for the method name.
         *
         * @return span name
         */
        String value() default "%1$s.%2$s";

        /**
         * Span kind. Defaults to {@link io.helidon.tracing.Span.Kind#INTERNAL}.
         * Note that if kind is set on a type to anything else than internal, it cannot be changed to internal
         * on method level (i.e. internal is default, so we use the value from parent).
         * <p>
         * If a method needs to use internal span kind, there must not be a type {@link io.helidon.tracing.Tracing.Traced}
         * annotation.
         *
         * @return kind of the span(s) to create
         */
        Span.Kind kind() default Span.Kind.INTERNAL;

        /**
         * Tags with fixed values that will be added to this span.
         *
         * @return tags
         */
        Tag[] tags() default {};
    }

    /**
     * Tracing tag.
     * Tags are added to spans (alternative term is Span Attribute).
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        /**
         * Tag key.
         *
         * @return tag key
         */
        String key();

        /**
         * Tag value.
         *
         * @return tag value
         */
        String value();
    }

    /**
     * Tracing tag declared as a method parameter.
     * Tags are added to spans (alternative term is Span Attribute).
     * <p>
     * Tag key is either explicitly defined, or inferred from the parameter name, tag value is the parameter value.
     * <p>
     * Note that when this annotation is used with runtime processing (i.e. in Helidon MP), the value MUST be defined,
     * as names of parameters are not retained in compiled Java classes.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ParamTag {
        /**
         * Tag key. Defaults to parameter name.
         *
         * @return tag key
         */
        String value() default "";
    }
}

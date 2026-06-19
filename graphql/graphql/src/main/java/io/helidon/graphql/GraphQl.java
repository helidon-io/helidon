/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.graphql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * Common GraphQL annotations shared by client and server integrations.
 */
@Api.Incubating
@Api.Since("27.0.0")
public final class GraphQl {
    private GraphQl() {
    }

    /**
     * Marks a GraphQL query operation method.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.EntryPoint
    public @interface Query {
    }

    /**
     * Marks a GraphQL mutation operation method.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.EntryPoint
    public @interface Mutation {
    }

    /**
     * Reserved for future GraphQL subscription support.
     * <p>
     * Subscription execution is not implemented by the current declarative GraphQL server generator. The annotation is
     * ignored when generating a schema.
     *
     * @deprecated subscription execution is reserved for future GraphQL support and is currently ignored
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Deprecated(since = "27.0.0")
    public @interface Subscription {
    }

    /**
     * Marks a Java type that should be included in generated GraphQL schema output.
     * <p>
     * Resolver return types and object fields that use Java classes, records, interfaces, or enums must reference types
     * annotated with this annotation before the declarative GraphQL generator emits SDL for them.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Entity {
    }

    /**
     * Names a GraphQL resolver argument parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface Argument {
        /**
         * Argument name.
         *
         * @return argument name
         */
        String value();
    }

    /**
     * Overrides the generated GraphQL name.
     */
    @Target({
            ElementType.TYPE,
            ElementType.METHOD,
            ElementType.FIELD,
            ElementType.PARAMETER,
            ElementType.RECORD_COMPONENT
    })
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Name {
        /**
         * GraphQL name.
         *
         * @return GraphQL name
         */
        String value();
    }

    /**
     * Adds a GraphQL description.
     */
    @Target({
            ElementType.TYPE,
            ElementType.METHOD,
            ElementType.FIELD,
            ElementType.PARAMETER,
            ElementType.RECORD_COMPONENT
    })
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Description {
        /**
         * Description text.
         *
         * @return description text
         */
        String value();
    }

    /**
     * Defines a default value for a resolver argument.
     * <p>
     * The initial declarative GraphQL server generator supports this annotation on resolver parameters only. Input record
     * component defaults are rejected during code generation.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface DefaultValue {
        /**
         * GraphQL default value literal.
         *
         * @return default value literal
         */
        String value();
    }

    /**
     * Marks a generated GraphQL type, field, or argument as non-null.
     */
    @Target({
            ElementType.TYPE_USE,
            ElementType.METHOD,
            ElementType.FIELD,
            ElementType.PARAMETER,
            ElementType.RECORD_COMPONENT
    })
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface NonNull {
    }

    /**
     * Excludes an output Java member from generated schema output.
     * <p>
     * The declarative server generator rejects this annotation on GraphQL input record components, because input records
     * must provide every constructor component.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Ignore {
    }

    /**
     * Marks a Java type as a GraphQL scalar.
     * <p>
     * Server and client integrations use a matching {@link io.helidon.graphql.spi.GraphQlScalar} Service Registry
     * implementation to convert values of the annotated Java type.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Scalar {
        /**
         * Scalar name. When empty, the Java type name is used.
         *
         * @return scalar name
         */
        String value() default "";
    }
}

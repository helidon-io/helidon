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

package io.helidon.webserver.graphql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * Declarative GraphQL server annotations.
 */
@Api.Preview
@Api.Since("27.0.0")
public final class GraphQlServer {
    /**
     * Default GraphQL web context.
     */
    public static final String DEFAULT_CONTEXT = "/graphql";

    /**
     * Default schema endpoint URI under the GraphQL web context.
     */
    public static final String DEFAULT_SCHEMA_URI = "/schema.graphql";

    private GraphQlServer() {
    }

    /**
     * Definition of a GraphQL server endpoint.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface Endpoint {
    }

    /**
     * Listener socket assigned to this endpoint.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    public @interface Listener {
        /**
         * Name of a routing to bind this application/service to.
         *
         * @return name of a routing on WebServer
         */
        String value();
    }

    /**
     * Web context for the GraphQL endpoint.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    public @interface Context {
        /**
         * GraphQL web context.
         *
         * @return GraphQL web context
         */
        String value() default DEFAULT_CONTEXT;
    }

    /**
     * Schema endpoint URI under the GraphQL web context.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    public @interface SchemaUri {
        /**
         * Schema endpoint URI.
         *
         * @return schema endpoint URI
         */
        String value() default DEFAULT_SCHEMA_URI;
    }

    /**
     * Exposes a child field resolver for a GraphQL type.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.EntryPoint
    public @interface Field {
        /**
         * GraphQL field name. When empty, the Java method name is used.
         *
         * @return GraphQL field name
         */
        String value() default "";
    }

    /**
     * Marks the source object parameter for a child field resolver.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface Source {
    }
}

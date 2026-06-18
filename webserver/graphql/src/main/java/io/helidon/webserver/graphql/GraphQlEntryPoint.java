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

import java.util.List;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

/**
 * Container class for types related to GraphQL resolver entry points.
 * <p>
 * NOTE: this API is part of incubating features of Helidon. This API may change including backward incompatible changes
 * and full removal. We welcome feedback for incubating features.
 */
@Api.Incubating
@Api.Since("27.0.0")
public final class GraphQlEntryPoint {
    private GraphQlEntryPoint() {
    }

    /**
     * Interceptor of a GraphQL resolver entry point.
     */
    public interface Interceptor {
        /**
         * Method to implement interceptor logic.
         *
         * @param interceptionContext context with invocation metadata
         * @param chain invocation chain, interceptor must call
         *        {@link io.helidon.webserver.graphql.GraphQlEntryPoint.Interceptor.Chain#proceed
         *        (DataFetchingEnvironment)}
         * @param environment GraphQL Java data fetching environment
         * @return resolver result
         * @throws java.lang.Exception in case the invocation fails, or the interceptor needs to stop processing with an
         *         exception
         */
        Object proceed(InterceptionContext interceptionContext,
                       Chain chain,
                       DataFetchingEnvironment environment) throws Exception;

        /**
         * Invocation chain for GraphQL entry point.
         */
        interface Chain extends Interception.Interceptor.Chain<Object> {
            @Override
            default Object proceed(Object[] args) throws Exception {
                return proceed((DataFetchingEnvironment) args[0]);
            }

            /**
             * Invoke the next interceptor in the chain.
             *
             * @param environment GraphQL Java data fetching environment
             * @return resolver result
             * @throws java.lang.Exception in case the invocation fails, this should not be handled by most interceptors
             */
            Object proceed(DataFetchingEnvironment environment) throws Exception;
        }
    }

    /**
     * A contract used from generated code to invoke GraphQL entry point interceptors.
     */
    public interface EntryPoints {
        /**
         * Data fetcher that triggers interceptors.
         *
         * @param descriptor descriptor of the invoked endpoint
         * @param typeQualifiers qualifiers of the invoked endpoint
         * @param typeAnnotations type annotations of the invoked endpoint
         * @param methodInfo method information of the resolver method
         * @param actualDataFetcher data fetcher that invokes the resolver method
         * @return data fetcher to register with GraphQL Java runtime wiring
         */
        DataFetcher<?> dataFetcher(ServiceDescriptor<?> descriptor,
                                   Set<Qualifier> typeQualifiers,
                                   List<Annotation> typeAnnotations,
                                   TypedElementInfo methodInfo,
                                   DataFetcher<?> actualDataFetcher);
    }
}

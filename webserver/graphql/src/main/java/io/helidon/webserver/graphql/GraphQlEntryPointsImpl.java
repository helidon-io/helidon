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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

@Service.Singleton
class GraphQlEntryPointsImpl implements GraphQlEntryPoint.EntryPoints {
    private final boolean noInterceptors;
    private final List<GraphQlEntryPoint.Interceptor> interceptors;

    @Service.Inject
    GraphQlEntryPointsImpl(List<ServiceInstance<Interception.EntryPointInterceptor>> entryPointInterceptors,
                           List<ServiceInstance<GraphQlEntryPoint.Interceptor>> graphQlEntryPointInterceptors) {
        this.noInterceptors = entryPointInterceptors.isEmpty() && graphQlEntryPointInterceptors.isEmpty();
        this.interceptors = merge(entryPointInterceptors, graphQlEntryPointInterceptors);
    }

    @Override
    public DataFetcher<?> dataFetcher(ServiceDescriptor<?> descriptor,
                                      Set<Qualifier> typeQualifiers,
                                      List<Annotation> typeAnnotations,
                                      TypedElementInfo methodInfo,
                                      DataFetcher<?> actualDataFetcher) {

        if (noInterceptors) {
            return actualDataFetcher;
        }

        InterceptionContext ctx = InterceptionContext.builder()
                .typeAnnotations(typeAnnotations)
                .elementInfo(methodInfo)
                .serviceInfo(descriptor)
                .build();

        return new EntryPointDataFetcher(ctx, interceptors, actualDataFetcher);
    }

    private static List<GraphQlEntryPoint.Interceptor> merge(
            List<ServiceInstance<Interception.EntryPointInterceptor>> entryPoints,
            List<ServiceInstance<GraphQlEntryPoint.Interceptor>> graphQlEntryPoints) {

        List<WeightedInterceptor> merged = new ArrayList<>();
        graphQlEntryPoints.stream()
                .map(it -> new WeightedInterceptor(it.get(), it.weight()))
                .forEach(merged::add);

        entryPoints.stream()
                .map(it -> new WeightedInterceptor(toGraphQlEntryPoint(it.get()), it.weight()))
                .forEach(merged::add);
        Weights.sort(merged);

        return merged.stream()
                .map(WeightedInterceptor::interceptor)
                .collect(Collectors.toUnmodifiableList());
    }

    private static GraphQlEntryPoint.Interceptor toGraphQlEntryPoint(
            Interception.EntryPointInterceptor entryPointInterceptor) {
        return entryPointInterceptor::proceed;
    }

    private record WeightedInterceptor(GraphQlEntryPoint.Interceptor interceptor,
                                       double weight) implements Weighted {
    }

    private static class EntryPointDataFetcher implements DataFetcher<Object> {
        private final InterceptionContext ctx;
        private final List<GraphQlEntryPoint.Interceptor> interceptors;
        private final DataFetcher<?> actualDataFetcher;

        private EntryPointDataFetcher(InterceptionContext ctx,
                                      List<GraphQlEntryPoint.Interceptor> interceptors,
                                      DataFetcher<?> actualDataFetcher) {
            this.ctx = ctx;
            this.interceptors = interceptors;
            this.actualDataFetcher = actualDataFetcher;
        }

        @Override
        public Object get(DataFetchingEnvironment environment) throws Exception {
            var graphQlContext = environment.getGraphQlContext();
            if (graphQlContext != null) {
                GraphQlService.ResolverInvocation invocation = graphQlContext.get(GraphQlService.RESOLVER_INVOCATION_KEY);
                if (invocation != null) {
                    invocation.markInvoked();
                }
            }
            return createChain().proceed(environment);
        }

        private GraphQlEntryPoint.Interceptor.Chain createChain() {
            return new Invocation(ctx, interceptors, actualDataFetcher);
        }
    }

    private static class Invocation implements GraphQlEntryPoint.Interceptor.Chain {
        private final InterceptionContext ctx;
        private final List<GraphQlEntryPoint.Interceptor> interceptors;
        private final DataFetcher<?> actualDataFetcher;

        private int interceptorPos;

        private Invocation(InterceptionContext ctx,
                           List<GraphQlEntryPoint.Interceptor> interceptors,
                           DataFetcher<?> actualDataFetcher) {
            this.ctx = ctx;
            this.interceptors = interceptors;
            this.actualDataFetcher = actualDataFetcher;
        }

        @Override
        public Object proceed(DataFetchingEnvironment environment) throws Exception {
            if (interceptorPos < interceptors.size()) {
                var interceptor = interceptors.get(interceptorPos);
                interceptorPos++;
                try {
                    return interceptor.proceed(ctx, this, environment);
                } catch (Exception e) {
                    interceptorPos--;
                    throw e;
                }
            }
            return actualDataFetcher.get(environment);
        }

        @Override
        public String toString() {
            return String.valueOf(ctx.elementInfo());
        }
    }
}

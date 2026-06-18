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
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphQlEntryPointsImplTest {
    @Test
    void noInterceptorsUseDataFetcherDirectly() {
        GraphQlEntryPointsImpl entryPoints = new GraphQlEntryPointsImpl(List.of(), List.of());
        DataFetcher<String> dataFetcher = _ -> "actual";

        DataFetcher<?> wrapped = entryPoints.dataFetcher(mock(ServiceDescriptor.class),
                                                         Set.of(),
                                                         List.<Annotation>of(),
                                                         mock(TypedElementInfo.class),
                                                         dataFetcher);

        assertThat(wrapped, sameInstance(dataFetcher));
    }

    @Test
    void graphQlInterceptorsWrapDataFetcher() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        GraphQlEntryPoint.Interceptor interceptor = (_, chain, environment) -> {
            invocations.incrementAndGet();
            return "wrapped-" + chain.proceed(environment);
        };
        GraphQlEntryPointsImpl entryPoints = new GraphQlEntryPointsImpl(List.of(),
                                                                        List.of(serviceInstance(interceptor)));

        DataFetcher<?> wrapped = entryPoints.dataFetcher(mock(ServiceDescriptor.class),
                                                         Set.of(),
                                                         List.<Annotation>of(),
                                                         mock(TypedElementInfo.class),
                                                         _ -> "actual");

        assertThat(wrapped.get(mock(DataFetchingEnvironment.class)), is("wrapped-actual"));
        assertThat(invocations.get(), is(1));
    }

    @SuppressWarnings("deprecation")
    @Test
    void genericEntryPointInterceptorsWrapDataFetcher() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        Interception.EntryPointInterceptor interceptor = new Interception.EntryPointInterceptor() {
            @Override
            public <T> T proceed(io.helidon.service.registry.InterceptionContext invocationContext,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object... args) throws Exception {
                invocations.incrementAndGet();
                return chain.proceed(args);
            }
        };
        GraphQlEntryPointsImpl entryPoints = new GraphQlEntryPointsImpl(List.of(genericServiceInstance(interceptor)),
                                                                        List.of());

        DataFetcher<?> wrapped = entryPoints.dataFetcher(mock(ServiceDescriptor.class),
                                                         Set.of(),
                                                         List.<Annotation>of(),
                                                         mock(TypedElementInfo.class),
                                                         _ -> "actual");

        assertThat(wrapped.get(mock(DataFetchingEnvironment.class)), is("actual"));
        assertThat(invocations.get(), is(1));
    }

    @SuppressWarnings("unchecked")
    private static ServiceInstance<GraphQlEntryPoint.Interceptor> serviceInstance(
            GraphQlEntryPoint.Interceptor interceptor) {
        ServiceInstance<GraphQlEntryPoint.Interceptor> instance = mock(ServiceInstance.class);
        when(instance.get()).thenReturn(interceptor);
        when(instance.weight()).thenReturn(100.0);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static ServiceInstance<Interception.EntryPointInterceptor> genericServiceInstance(
            Interception.EntryPointInterceptor interceptor) {
        ServiceInstance<Interception.EntryPointInterceptor> instance = mock(ServiceInstance.class);
        when(instance.get()).thenReturn(interceptor);
        when(instance.weight()).thenReturn(100.0);
        return instance;
    }
}

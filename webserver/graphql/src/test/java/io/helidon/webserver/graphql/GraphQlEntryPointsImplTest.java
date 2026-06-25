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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
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
    @Test
    void genericEntryPointSecurityFailureBecomesFieldError() {
        Interception.EntryPointInterceptor interceptor = new Interception.EntryPointInterceptor() {
            @Override
            public <T> T proceed(io.helidon.service.registry.InterceptionContext invocationContext,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object... args) throws Exception {
                if ("secret".equals(invocationContext.elementInfo().elementName())) {
                    throw new SecurityException("resolver denied");
                }
                return chain.proceed(args);
            }
        };
        GraphQlEntryPointsImpl entryPoints = new GraphQlEntryPointsImpl(List.of(genericServiceInstance(interceptor)),
                                                                        List.of());
        DataFetcher<?> publicFetcher = entryPoints.dataFetcher(mock(ServiceDescriptor.class),
                                                               Set.of(),
                                                               List.<Annotation>of(),
                                                               methodInfo("publicValue"),
                                                               _ -> "public");
        DataFetcher<?> secretFetcher = entryPoints.dataFetcher(mock(ServiceDescriptor.class),
                                                               Set.of(),
                                                               List.<Annotation>of(),
                                                               methodInfo("secret"),
                                                               _ -> "secret");
        InvocationHandler handler = InvocationHandler.create(schema(publicFetcher, secretFetcher));

        Map<String, Object> response = handler.execute("{publicValue secret}");

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertThat(data.get("publicValue"), is("public"));
        assertThat(data.get("secret"), nullValue());
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.get("errors");
        assertThat(errors.size(), is(1));
        assertThat(errors.getFirst().get("message"), is("Server Error"));
        assertThat(String.valueOf(errors.getFirst().get("message")), not(containsString("resolver denied")));
        assertThat(errors.getFirst().get("path"), is(List.of("secret")));
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

    private static TypedElementInfo methodInfo(String name) {
        return TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(TypeNames.STRING)
                .build();
    }

    private static GraphQLSchema schema(DataFetcher<?> publicFetcher, DataFetcher<?> secretFetcher) {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser()
                .parse("type Query { publicValue: String secret: String }");
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("publicValue", publicFetcher)
                        .dataFetcher("secret", secretFetcher))
                .build();
        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }
}

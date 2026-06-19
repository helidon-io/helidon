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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.Weighted;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.webserver.WebServer;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for GraphQL WebServer execution and resolver entry point wrapping.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class GraphQlJmhBenchmark {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final String QUERY = "{ hello numbers }";
    private static final String ENCODED_QUERY = URLEncoder.encode(QUERY, StandardCharsets.UTF_8);
    private static final ServiceDescriptor<Object> DESCRIPTOR = new BenchmarkDescriptor();
    private static final TypedElementInfo WRAPPED_METHOD = TypedElementInfo.builder()
            .kind(ElementKind.METHOD)
            .elementName("resolverWrapped")
            .typeName(TypeNames.STRING)
            .build();

    @Benchmark
    public void graphQlServiceGet(GraphQlState state, Blackhole blackhole) throws IOException, InterruptedException {
        HttpResponse<String> response = state.httpClient.send(state.getRequest, HttpResponse.BodyHandlers.ofString());
        blackhole.consume(response.statusCode());
        blackhole.consume(response.body());
    }

    @Benchmark
    public void graphQlWrappedResolver(GraphQlState state, Blackhole blackhole) {
        Map<String, Object> result = state.invocationHandler.execute("{ resolverWrapped }");
        blackhole.consume(result);
    }

    @State(Scope.Benchmark)
    public static class GraphQlState {
        private WebServer server;
        private HttpClient httpClient;
        private HttpRequest getRequest;
        private InvocationHandler invocationHandler;

        @Setup
        public void setup() {
            LogConfig.configureRuntime();

            GraphQLSchema schema = schema();
            invocationHandler = InvocationHandler.create(schema);
            server = WebServer.builder()
                    .host(SERVER_HOST)
                    .routing(routing -> routing.register(GraphQlService.create(schema)))
                    .build()
                    .start();
            httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            getRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://" + SERVER_HOST + ":" + server.port() + "/graphql?query=" + ENCODED_QUERY))
                    .build();
        }

        @TearDown
        public void tearDown() {
            server.stop();
        }
    }

    private static GraphQLSchema schema() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser()
                .parse("""
                        type Query {
                            hello: String
                            numbers: [Int]
                            resolverWrapped: String
                        }
                        """);
        GraphQlEntryPointsImpl entryPoints = new GraphQlEntryPointsImpl(List.of(),
                                                                        List.of(new BenchmarkInstance<>(interceptor())));
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("hello", _ -> "world")
                        .dataFetcher("numbers", _ -> List.of(1, 2, 3))
                        .dataFetcher("resolverWrapped",
                                     entryPoints.dataFetcher(DESCRIPTOR,
                                                             Set.of(),
                                                             List.of(),
                                                             WRAPPED_METHOD,
                                                             _ -> "wrapped")))
                .build();
        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    private static GraphQlEntryPoint.Interceptor interceptor() {
        return (_, chain, environment) -> chain.proceed(environment);
    }

    private record BenchmarkInstance<T>(T instance) implements ServiceInstance<T> {
        @Override
        public T get() {
            return instance;
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return Set.of();
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of();
        }

        @Override
        public TypeName scope() {
            return Service.Singleton.TYPE;
        }

        @Override
        public double weight() {
            return Weighted.DEFAULT_WEIGHT;
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create(instance.getClass());
        }
    }

    private static final class BenchmarkDescriptor implements ServiceDescriptor<Object> {
        private static final TypeName TYPE = TypeName.create(GraphQlJmhBenchmark.class);
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(BenchmarkDescriptor.class);

        @Override
        public TypeName serviceType() {
            return TYPE;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public TypeName scope() {
            return Service.Singleton.TYPE;
        }
    }
}

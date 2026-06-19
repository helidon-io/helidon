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

package io.helidon.webserver.security;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for GraphQL resolver entry points passing through WebServer security.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class GraphQlSecurityJmhBenchmark {
    private static final TypeName AUTHORIZED = TypeName.create("io.helidon.security.annotations.Authorized");
    private static final ServiceInfo SERVICE_INFO = new BenchmarkServiceInfo();

    @Benchmark
    public void genericNoSecurityCached(SecurityState state, Blackhole blackhole) throws Exception {
        blackhole.consume(state.noSecurityInterceptor.proceed(state.publicResolver, state.chain, "environment"));
    }

    @Benchmark
    public void genericAuthorized(SecurityState state, Blackhole blackhole) throws Exception {
        String result = Contexts.runInContextWithThrow(state.context,
                                                       () -> state.authorizedInterceptor.proceed(state.securedResolver,
                                                                                                 state.chain,
                                                                                                 "environment"));
        blackhole.consume(result);
    }

    @State(Scope.Benchmark)
    public static class SecurityState {
        private HttpSecurityInterceptor noSecurityInterceptor;
        private HttpSecurityInterceptor authorizedInterceptor;
        private InterceptionContext publicResolver;
        private InterceptionContext securedResolver;
        private Interception.Interceptor.Chain<String> chain;
        private Context context;

        @Setup
        public void setup() throws Exception {
            Security noSecurity = Security.builder().build();
            Security security = Security.builder()
                    .addAuthorizationProvider(_ -> AuthorizationResponse.permit())
                    .build();
            SecurityContext securityContext = security.contextBuilder("graphql")
                    .env(SecurityEnvironment.builder(security.serverTime())
                                 .method("POST")
                                 .path("/graphql")
                                 .targetUri(URI.create("http://localhost/graphql"))
                                 .build())
                    .build();

            noSecurityInterceptor = new HttpSecurityInterceptor(noSecurity,
                                                                Config.just(ConfigSources.create(Map.of(
                                                                        "server.features.security.declarative"
                                                                                + ".authorize-annotated-only",
                                                                        "true"))),
                                                                List.of(),
                                                                List.of());
            authorizedInterceptor = new HttpSecurityInterceptor(security, Config.empty(), List.of(), List.of());
            publicResolver = interceptionContext("publicResolver");
            securedResolver = interceptionContext("securedResolver", Annotation.create(AUTHORIZED));
            chain = _ -> "actual";
            context = Context.create();
            context.register(securityContext);
            noSecurityInterceptor.proceed(publicResolver, chain, "environment");
        }
    }

    private static InterceptionContext interceptionContext(String methodName, Annotation... annotations) {
        TypedElementInfo.Builder method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(methodName)
                .typeName(TypeNames.STRING);
        for (Annotation annotation : annotations) {
            method.addAnnotation(annotation);
        }

        return InterceptionContext.builder()
                .serviceInfo(SERVICE_INFO)
                .elementInfo(method.build())
                .build();
    }

    private static final class BenchmarkServiceInfo implements ServiceInfo {
        private static final TypeName SERVICE_TYPE = TypeName.create(GraphQlSecurityJmhBenchmark.class);
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(BenchmarkServiceInfo.class);

        @Override
        public TypeName serviceType() {
            return SERVICE_TYPE;
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

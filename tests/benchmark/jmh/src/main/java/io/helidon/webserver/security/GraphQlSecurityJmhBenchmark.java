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

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
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
    @Threads(4)
    public void genericNoSecurityCachedConcurrent(SecurityState state, Blackhole blackhole) throws Exception {
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

    @Benchmark
    public void httpNoSecurityCached(SecurityState state, Blackhole blackhole) throws Exception {
        blackhole.consume(state.noSecurityInterceptor.proceed(state.publicHttp,
                                                             state.httpChain,
                                                             state.request,
                                                             state.response));
    }

    @Benchmark
    public void httpAuthorized(SecurityState state, Blackhole blackhole) throws Exception {
        blackhole.consume(state.authorizedInterceptor.proceed(state.securedHttp,
                                                             state.httpChain,
                                                             state.request,
                                                             state.response));
    }

    @State(Scope.Benchmark)
    public static class SecurityState {
        private HttpSecurityInterceptor noSecurityInterceptor;
        private HttpSecurityInterceptor authorizedInterceptor;
        private InterceptionContext publicResolver;
        private InterceptionContext securedResolver;
        private InterceptionContext publicHttp;
        private InterceptionContext securedHttp;
        private Interception.Interceptor.Chain<String> chain;
        private Interception.Interceptor.Chain<Void> httpChain;
        private Context context;
        private ServerRequest request;
        private ServerResponse response;

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
            publicHttp = interceptionContext("publicHttp");
            securedHttp = interceptionContext("securedHttp", Annotation.create(AUTHORIZED));
            chain = _ -> "actual";
            httpChain = _ -> null;
            context = Context.create();
            context.register(securityContext);
            request = request(context);
            response = proxy(ServerResponse.class, Map.of());
            noSecurityInterceptor.proceed(publicResolver, chain, "environment");
            noSecurityInterceptor.proceed(publicHttp, httpChain, request, response);
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

    private static ServerRequest request(Context context) {
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.POST, "/graphql", true);
        UriQuery query = prologue.query();
        RoutedPath path = routedPath("/graphql");
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        writableHeaders.set(HeaderNames.HOST, "localhost");

        return proxy(ServerRequest.class,
                     Map.of("context", context,
                            "prologue", prologue,
                            "path", path,
                            "requestedUri", UriInfo.builder()
                                    .scheme("http")
                                    .host("localhost")
                                    .port(80)
                                    .path(UriPath.create("/graphql"))
                                    .query(query)
                                    .build(),
                            "query", query,
                            "headers", ServerRequestHeaders.create(writableHeaders),
                            "remotePeer", peerInfo(),
                            "isSecure", false));
    }

    private static RoutedPath routedPath(String path) {
        RoutedPath[] holder = new RoutedPath[1];
        holder[0] = proxy(RoutedPath.class, Map.of("path", path,
                                                   "rawPath", path,
                                                   "absolute", holder));
        return holder[0];
    }

    private static PeerInfo peerInfo() {
        return proxy(PeerInfo.class, Map.of("host", "127.0.0.1",
                                            "port", 8080));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            String methodName = method.getName();
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return type.getSimpleName();
            }

            Object value = values.get(methodName);
            if (value instanceof Object[] holder) {
                return holder[0];
            }
            if (value != null || values.containsKey(methodName)) {
                return value;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
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

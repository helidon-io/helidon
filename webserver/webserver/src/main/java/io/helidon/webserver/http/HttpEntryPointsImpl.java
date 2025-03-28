/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.EntryPointInterceptor;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;

@Service.Singleton
class HttpEntryPointsImpl implements HttpEntryPoint.EntryPoints {
    private final boolean noInterceptors;
    private final List<HttpEntryPoint.Interceptor> interceptors;

    @Service.Inject
    HttpEntryPointsImpl(List<ServiceInstance<EntryPointInterceptor>> entryPointInterceptors,
                        List<ServiceInstance<HttpEntryPoint.Interceptor>> httpEntryPointInterceptors) {
        this.noInterceptors = entryPointInterceptors.isEmpty() && httpEntryPointInterceptors.isEmpty();
        this.interceptors = merge(entryPointInterceptors, httpEntryPointInterceptors);
    }

    @Override
    public Handler handler(ServiceDescriptor<?> descriptor,
                           Set<Qualifier> typeQualifiers,
                           List<Annotation> typeAnnotations,
                           TypedElementInfo methodInfo,
                           Handler actualHandler) {

        if (noInterceptors) {
            return actualHandler;
        }

        InterceptionContext ctx = InterceptionContext.builder()
                .typeAnnotations(typeAnnotations)
                .elementInfo(methodInfo)
                .serviceInfo(descriptor)
                .build();

        return new EntryPointHandler(ctx, interceptors, actualHandler);
    }

    private static List<HttpEntryPoint.Interceptor> merge(List<ServiceInstance<EntryPointInterceptor>> entryPoints,
                                                          List<ServiceInstance<HttpEntryPoint.Interceptor>> httpEntryPoints) {

        List<WeightedInterceptor> merged = new ArrayList<>();
        httpEntryPoints.stream()
                .map(it -> new WeightedInterceptor(it.get(),
                                                   it.weight()))
                .forEach(merged::add);

        entryPoints.stream()
                .map(it -> new WeightedInterceptor(toHttpEntryPoint(it.get()), it.weight()))
                .forEach(merged::add);
        Weights.sort(merged);

        return merged.stream()
                .map(WeightedInterceptor::interceptor)
                .collect(Collectors.toUnmodifiableList());
    }

    private static HttpEntryPoint.Interceptor toHttpEntryPoint(EntryPointInterceptor entryPointInterceptor) {
        return (invocationContext, chain, request, response) -> {
            entryPointInterceptor.proceed(invocationContext, chain, request, response);
        };
    }

    private record WeightedInterceptor(HttpEntryPoint.Interceptor interceptor,
                                       double weight) implements Weighted {
    }

    private static class EntryPointHandler implements Handler {
        private final InterceptionContext ctx;
        private final List<HttpEntryPoint.Interceptor> interceptors;
        private final Handler actualHandler;

        private EntryPointHandler(InterceptionContext ctx, List<HttpEntryPoint.Interceptor> interceptors, Handler actualHandler) {
            this.ctx = ctx;
            this.interceptors = interceptors;
            this.actualHandler = actualHandler;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            createChain().proceed(req, res);
        }

        private HttpEntryPoint.Interceptor.Chain createChain() {
            return new Invocation(ctx, interceptors, actualHandler);
        }
    }

    private static class Invocation implements HttpEntryPoint.Interceptor.Chain {
        private final InterceptionContext ctx;
        private final List<HttpEntryPoint.Interceptor> interceptors;
        private final Handler actualHandler;

        private int interceptorPos;

        private Invocation(InterceptionContext ctx, List<HttpEntryPoint.Interceptor> interceptors, Handler actualHandler) {
            this.ctx = ctx;
            this.interceptors = interceptors;
            this.actualHandler = actualHandler;
        }

        @Override
        public void proceed(ServerRequest request, ServerResponse response) throws Exception {
            if (interceptorPos < interceptors.size()) {
                var interceptor = interceptors.get(interceptorPos);
                interceptorPos++;
                try {
                    interceptor.proceed(ctx, this, request, response);
                    return;
                } catch (Exception e) {
                    interceptorPos--;
                    throw e;
                }
            }
            actualHandler.handle(request, response);
        }

        @Override
        public String toString() {
            return String.valueOf(ctx.elementInfo());
        }
    }
}

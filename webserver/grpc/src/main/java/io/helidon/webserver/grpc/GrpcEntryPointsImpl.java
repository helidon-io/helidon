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

package io.helidon.webserver.grpc;

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

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

@SuppressWarnings("deprecation")
@Service.Singleton
class GrpcEntryPointsImpl implements GrpcEntryPoint.EntryPoints {
    private static final ServerInterceptor NOOP = new NoopServerInterceptor();

    private final boolean noInterceptors;
    private final List<GrpcEntryPoint.Interceptor> interceptors;

    @Service.Inject
    GrpcEntryPointsImpl(List<ServiceInstance<Interception.EntryPointInterceptor>> entryPointInterceptors,
                        List<ServiceInstance<GrpcEntryPoint.Interceptor>> grpcEntryPointInterceptors) {
        this.noInterceptors = entryPointInterceptors.isEmpty() && grpcEntryPointInterceptors.isEmpty();
        this.interceptors = merge(entryPointInterceptors, grpcEntryPointInterceptors);
    }

    @Override
    public ServerInterceptor interceptor(ServiceDescriptor<?> descriptor,
                                         Set<Qualifier> typeQualifiers,
                                         List<Annotation> typeAnnotations,
                                         TypedElementInfo methodInfo) {

        if (noInterceptors) {
            return NOOP;
        }

        InterceptionContext ctx = InterceptionContext.builder()
                .typeAnnotations(typeAnnotations)
                .elementInfo(methodInfo)
                .serviceInfo(descriptor)
                .build();

        return new EntryPointInterceptor(ctx, interceptors);
    }

    private static List<GrpcEntryPoint.Interceptor> merge(
            List<ServiceInstance<Interception.EntryPointInterceptor>> entryPoints,
            List<ServiceInstance<GrpcEntryPoint.Interceptor>> grpcEntryPoints) {

        List<WeightedInterceptor> merged = new ArrayList<>();
        grpcEntryPoints.stream()
                .map(it -> new WeightedInterceptor(it.get(), it.weight()))
                .forEach(merged::add);

        entryPoints.stream()
                .map(it -> new WeightedInterceptor(toGrpcEntryPoint(it.get()), it.weight()))
                .forEach(merged::add);
        Weights.sort(merged);

        return merged.stream()
                .map(WeightedInterceptor::interceptor)
                .collect(Collectors.toUnmodifiableList());
    }

    private static GrpcEntryPoint.Interceptor toGrpcEntryPoint(Interception.EntryPointInterceptor entryPointInterceptor) {
        return new GrpcEntryPoint.Interceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> intercept(InterceptionContext interceptionContext,
                                                                     GrpcEntryPoint.Interceptor.Chain<ReqT, RespT> chain,
                                                                     ServerCall<ReqT, RespT> call,
                                                                     Metadata headers) throws Exception {
                return entryPointInterceptor.proceed(interceptionContext, chain, call, headers);
            }
        };
    }

    private record WeightedInterceptor(GrpcEntryPoint.Interceptor interceptor,
                                       double weight) implements Weighted {
    }

    private static class NoopServerInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    private static class EntryPointInterceptor implements ServerInterceptor {
        private final InterceptionContext ctx;
        private final List<GrpcEntryPoint.Interceptor> interceptors;

        private EntryPointInterceptor(InterceptionContext ctx, List<GrpcEntryPoint.Interceptor> interceptors) {
            this.ctx = ctx;
            this.interceptors = interceptors;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            try {
                return createChain(next).proceed(call, headers);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                call.close(Status.INTERNAL.withCause(e).withDescription(e.getMessage()), new Metadata());
                return new ServerCall.Listener<>() {
                };
            }
        }

        private <ReqT, RespT> GrpcEntryPoint.Interceptor.Chain<ReqT, RespT> createChain(
                ServerCallHandler<ReqT, RespT> next) {
            return new Invocation<>(ctx, interceptors, next);
        }
    }

    private static class Invocation<ReqT, RespT> implements GrpcEntryPoint.Interceptor.Chain<ReqT, RespT> {
        private final InterceptionContext ctx;
        private final List<GrpcEntryPoint.Interceptor> interceptors;
        private final ServerCallHandler<ReqT, RespT> next;

        private int interceptorPos;

        private Invocation(InterceptionContext ctx,
                           List<GrpcEntryPoint.Interceptor> interceptors,
                           ServerCallHandler<ReqT, RespT> next) {
            this.ctx = ctx;
            this.interceptors = interceptors;
            this.next = next;
        }

        @Override
        public ServerCall.Listener<ReqT> proceed(ServerCall<ReqT, RespT> call, Metadata headers) throws Exception {
            if (interceptorPos < interceptors.size()) {
                var interceptor = interceptors.get(interceptorPos);
                interceptorPos++;
                try {
                    return interceptor.intercept(ctx, this, call, headers);
                } catch (Exception e) {
                    interceptorPos--;
                    throw e;
                }
            }
            return next.startCall(call, headers);
        }

        @Override
        public String toString() {
            return String.valueOf(ctx.elementInfo());
        }
    }
}

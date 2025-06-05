/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;

import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

class GrpcServiceRoute extends GrpcRoute {

    private final String serviceName;
    private final List<GrpcRouteHandler<?, ?>> routes;
    private final Descriptors.FileDescriptor proto;

    private GrpcServiceRoute(GrpcService service, List<GrpcRouteHandler<?, ?>> routes) {
        this.serviceName = service.serviceName();
        this.routes = routes;
        this.proto = service.proto();
    }

    private GrpcServiceRoute(String serviceName, List<GrpcRouteHandler<?, ?>> routes) {
        this.serviceName = serviceName;
        this.routes = routes;
        this.proto = emptyProto();
    }

    @Override
    String serviceName() {
        return serviceName;
    }

    @Override
    public Descriptors.FileDescriptor proto() {
        return proto;
    }

    /**
     * Creates a gRPC route for an instance of {@link GrpcService}.
     *
     * @param service the service
     * @param interceptors the interceptors
     * @return the route
     */
    static GrpcRoute create(GrpcService service, WeightedBag<ServerInterceptor> interceptors) {
        Routing svcRouter = new Routing(service, interceptors);
        service.update(svcRouter);
        return svcRouter.build();
    }

    /**
     * Creates a gRPC route for an instance of {@link BindableService}.
     *
     * @param service the service
     * @param interceptors the interceptors
     * @return the route
     */
    static GrpcRoute create(BindableService service, WeightedBag<ServerInterceptor> interceptors) {
        ServerServiceDefinition definition = service.bindService();
        String serviceName = definition.getServiceDescriptor().getName();
        List<GrpcRouteHandler<?, ?>> routes = new LinkedList<>();
        service.bindService().getMethods().forEach(
                method -> routes.add(GrpcRouteHandler.bindableMethod(service, method, interceptors)));
        return new GrpcServiceRoute(serviceName, routes);
    }

    /**
     * Creates a gRPC route for an instance CDI bean annotated with {@link @Grpc}.
     * Registers interceptors for context on all the routes.
     *
     * @param service the service
     * @param interceptors interceptor bag
     * @return the route
     */
    static GrpcRoute create(GrpcServiceDescriptor service, WeightedBag<ServerInterceptor> interceptors) {
        return create(BindableServiceImpl.create(service), interceptors);
    }

    @Override
    GrpcRouteHandler<?, ?> handler(HttpPrologue prologue) {
        for (GrpcRouteHandler<?, ?> route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }
        throw new IllegalStateException("GrpcServiceRoute(" + serviceName
                + ") accepted prologue, but cannot provide route: " + prologue);
    }

    PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        for (GrpcRouteHandler<?, ?> route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return accepts;
            }
        }
        return PathMatchers.MatchResult.notAccepted();
    }

    private Descriptors.FileDescriptor emptyProto() {
        try {
            DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.getDefaultInstance();
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[] {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class Routing implements GrpcService.Routing {
        private final List<GrpcRouteHandler<?, ?>> routes = new LinkedList<>();
        private final GrpcService service;
        private final WeightedBag<ServerInterceptor> interceptors;

        Routing(GrpcService service, WeightedBag<ServerInterceptor> interceptors) {
            this.service = service;
            this.interceptors = interceptors;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing unary(String methodName, ServerCalls.UnaryMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.unary(service.proto(),
                                              service.serviceName(),
                                              methodName,
                                              method,
                                              interceptors));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing bidi(String methodName, ServerCalls.BidiStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.bidi(service.proto(),
                                             service.serviceName(),
                                             methodName,
                                             method,
                                             interceptors));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing serverStream(String methodName,
                                                             ServerCalls.ServerStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.serverStream(service.proto(),
                                                     service.serviceName(),
                                                     methodName,
                                                     method,
                                                     interceptors));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing clientStream(String methodName,
                                                             ServerCalls.ClientStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.clientStream(service.proto(),
                                                     service.serviceName(),
                                                     methodName,
                                                     method,
                                                     interceptors));
            return this;
        }

        public GrpcServiceRoute build() {
            return new GrpcServiceRoute(service, List.copyOf(routes));
        }
    }
}

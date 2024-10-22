/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

class GrpcServiceRoute extends GrpcRoute {

    private final String serviceName;
    private final List<GrpcRouteHandler<?, ?>> routes;

    private GrpcServiceRoute(String serviceName, List<GrpcRouteHandler<?, ?>> routes) {
        this.serviceName = serviceName;
        this.routes = routes;
    }

    /**
     * Creates a gRPC route for an instance of {@link GrpcService}.
     * A server interceptor chain will not be automatically associated
     * with calls to this service.
     *
     * @param service the service
     * @return the route
     */
    static GrpcRoute create(GrpcService service) {
        Routing svcRouter = new Routing(service);
        service.update(svcRouter);
        return svcRouter.build();
    }

    /**
     * Creates a gRPC route for an instance of {@link BindableService}.
     *
     * @param service the service
     * @return the route
     */
    static GrpcRoute create(BindableService service) {
        ServerServiceDefinition definition = service.bindService();
        String serviceName = definition.getServiceDescriptor().getName();
        List<GrpcRouteHandler<?, ?>> routes = new LinkedList<>();
        service.bindService().getMethods().forEach(
                method -> routes.add(GrpcRouteHandler.bindableMethod(service, method)));
        return new GrpcServiceRoute(serviceName, routes);
    }

    /**
     * Creates a gRPC route for an instance CDI bean annotated with {@link @Grpc}.
     * Registers global interceptors for context on all the routes.
     *
     * @param service the service
     * @param interceptors interceptor bag
     * @return the route
     */
    static GrpcRoute create(GrpcServiceDescriptor service, WeightedBag<ServerInterceptor> interceptors) {
        interceptors.add(ContextSettingServerInterceptor.create());
        return create(BindableServiceImpl.create(service, interceptors));
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

    static class Routing implements GrpcService.Routing {
        private final List<GrpcRouteHandler<?, ?>> routes = new LinkedList<>();
        private final Descriptors.FileDescriptor proto;
        private final String serviceName;

        Routing(GrpcService service) {
            this.proto = service.proto();
            this.serviceName = service.serviceName();
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing unary(String methodName, ServerCalls.UnaryMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.unary(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing bidi(String methodName, ServerCalls.BidiStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.bidi(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing serverStream(String methodName,
                                                             ServerCalls.ServerStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.serverStream(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing clientStream(String methodName,
                                                             ServerCalls.ClientStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.clientStream(proto, serviceName, methodName, method));
            return this;
        }

        public GrpcServiceRoute build() {
            return new GrpcServiceRoute(serviceName, List.copyOf(routes));
        }
    }
}

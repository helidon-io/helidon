/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;

import com.google.protobuf.Descriptors;
import io.grpc.stub.ServerCalls;

class GrpcServiceRoute extends GrpcRoute {
    private final String serviceName;
    private final List<Grpc<?, ?>> routes;

    private GrpcServiceRoute(String serviceName, List<Grpc<?, ?>> routes) {
        this.serviceName = serviceName;
        this.routes = routes;
    }

    static GrpcRoute create(GrpcService service) {
        Routing svcRouter = new Routing(service);
        service.update(svcRouter);
        return svcRouter.build();
    }

    @Override
    Grpc<?, ?> toGrpc(HttpPrologue prologue) {
        for (Grpc<?, ?> route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }
        throw new IllegalStateException("GrpcServiceRoute(" + serviceName + ") accepted prologue, but cannot provide route: "
                                                + prologue);
    }

    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        for (Grpc<?, ?> route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return accepts;
            }
        }
        return PathMatchers.MatchResult.notAccepted();
    }

    static class Routing implements GrpcService.Routing {
        private final List<Grpc<?, ?>> routes = new LinkedList<>();
        private final Descriptors.FileDescriptor proto;
        private final String serviceName;

        Routing(GrpcService service) {
            this.proto = service.proto();
            this.serviceName = service.serviceName();
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing unary(String methodName, ServerCalls.UnaryMethod<ReqT, ResT> method) {
            routes.add(Grpc.unary(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing unary(String methodName, GrpcServerCalls.Unary<ReqT, ResT> method) {
            return null;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing bidi(String methodName, ServerCalls.BidiStreamingMethod<ReqT, ResT> method) {
            routes.add(Grpc.bidi(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing bidi(String methodName, GrpcServerCalls.Bidi<ReqT, ResT> method) {
            return null;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing serverStream(String methodName,
                                                             ServerCalls.ServerStreamingMethod<ReqT, ResT> method) {
            routes.add(Grpc.serverStream(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing serverStream(String methodName, GrpcServerCalls.ServerStream<ReqT, ResT> method) {
            return null;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing clientStream(String methodName,
                                                             ServerCalls.ClientStreamingMethod<ReqT, ResT> method) {
            routes.add(Grpc.clientStream(proto, serviceName, methodName, method));
            return this;
        }

        @Override
        public <ReqT, ResT> GrpcService.Routing clientStream(String methodName, GrpcServerCalls.ClientStream<ReqT, ResT> method) {
            return null;
        }

        public GrpcServiceRoute build() {
            return new GrpcServiceRoute(serviceName, List.copyOf(routes));
        }
    }
}

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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Routing;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

/**
 * GRPC specific routing.
 */
public class GrpcRouting implements Routing {
    private static final GrpcRouting EMPTY = GrpcRouting.builder().build();

    private final ArrayList<GrpcRoute> routes;

    private GrpcRouting(Builder builder) {
        this.routes = new ArrayList<>(builder.routes);
    }

    @Override
    public Class<? extends Routing> routingType() { return GrpcRouting.class; }

    /**
     * New routing builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Empty grpc routing.
     *
     * @return empty routing
     */
    static GrpcRouting empty() {
        return EMPTY;
    }

    @Override
    public void beforeStart() {
        for (GrpcRoute route : routes) {
            route.beforeStart();
        }
    }

    @Override
    public void afterStop() {
        for (GrpcRoute route : routes) {
            route.afterStop();
        }
    }

    Grpc<?, ?> findRoute(HttpPrologue prologue) {
        for (GrpcRoute route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route.toGrpc(prologue);
            }
        }

        return null;
    }

    /**
     * Fluent API builder for {@link GrpcRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, GrpcRouting> {
        private final List<GrpcRoute> routes = new LinkedList<>();

        private Builder() {
        }

        @Override
        public GrpcRouting build() {
            return new GrpcRouting(this);
        }

        /**
         * Configure grpc service.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(GrpcService service) {
            return route(GrpcServiceRoute.create(service));
        }

        /**
         * Unary route.
         *
         * @param proto       proto descriptor
         * @param serviceName service name
         * @param methodName  method name
         * @param method      method to handle this route
         * @param <ReqT>      request type
         * @param <ResT>      response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder unary(Descriptors.FileDescriptor proto,
                                          String serviceName,
                                          String methodName,
                                          ServerCalls.UnaryMethod<ReqT, ResT> method) {

            return route(Grpc.unary(proto, serviceName, methodName, method));
        }

        /**
         * Bidirectional route.
         *
         * @param proto       proto descriptor
         * @param serviceName service name
         * @param methodName  method name
         * @param method      method to handle this route
         * @param <ReqT>      request type
         * @param <ResT>      response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder bidi(Descriptors.FileDescriptor proto,
                                         String serviceName,
                                         String methodName,
                                         ServerCalls.BidiStreamingMethod<ReqT, ResT> method) {
            return route(Grpc.bidi(proto, serviceName, methodName, method));
        }

        /**
         * Server streaming route.
         *
         * @param proto       proto descriptor
         * @param serviceName service name
         * @param methodName  method name
         * @param method      method to handle this route
         * @param <ReqT>      request type
         * @param <ResT>      response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder serverStream(Descriptors.FileDescriptor proto,
                                                 String serviceName,
                                                 String methodName,
                                                 ServerCalls.ServerStreamingMethod<ReqT, ResT> method) {

            return route(Grpc.serverStream(proto, serviceName, methodName, method));
        }

        /**
         * Client streaming route.
         *
         * @param proto       proto descriptor
         * @param serviceName service name
         * @param methodName  method name
         * @param method      method to handle this route
         * @param <ReqT>      request type
         * @param <ResT>      response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder clientStream(Descriptors.FileDescriptor proto,
                                                 String serviceName,
                                                 String methodName,
                                                 ServerCalls.ClientStreamingMethod<ReqT, ResT> method) {

            return route(Grpc.clientStream(proto, serviceName, methodName, method));
        }

        /**
         * Add all the routes for a {@link BindableService} service.
         *
         * @param proto    the proto descriptor
         * @param service  the {@link BindableService} to add routes for
         *
         * @return updated builder
         */
        public Builder service(Descriptors.FileDescriptor proto, BindableService service) {
            for (ServerMethodDefinition<?, ?> method : service.bindService().getMethods()) {
                route(Grpc.methodDefinition(method, proto));
            }
            return this;
        }

        /**
         * Add all the routes for the {@link ServerServiceDefinition} service.
         *
         * @param service  the {@link ServerServiceDefinition} to add routes for
         *
         * @return updated builder
         */
        public Builder service(ServerServiceDefinition service) {
            for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
                route(Grpc.methodDefinition(method, null));
            }
            return this;
        }

        private Builder route(GrpcRoute route) {
            routes.add(route);
            return this;
        }
    }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Routing;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

/**
 * GRPC specific routing.
 */
public class GrpcRouting implements Routing {
    private static final GrpcRouting EMPTY = GrpcRouting.builder().build();

    private final ArrayList<GrpcRoute> routes;
    private final WeightedBag<ServerInterceptor> interceptors;
    private final ArrayList<GrpcServiceDescriptor> services;

    private GrpcRouting(Builder builder) {
        this.routes = new ArrayList<>(builder.routes);
        this.interceptors = builder.interceptors;
        this.services = new ArrayList<>(builder.services.values());
    }

    @Override
    public Class<? extends Routing> routingType() {
        return GrpcRouting.class;
    }

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

    /**
     * Weighted bag of server interceptors associated with routing.
     *
     * @return weighted bag of server interceptors
     */
    public WeightedBag<ServerInterceptor> interceptors() {
        return interceptors;
    }

    /**
     * Obtain a {@link List} of the {@link GrpcServiceDescriptor} instances
     * contained in this {@link GrpcRouting}.
     *
     * @return a {@link List} of the {@link GrpcServiceDescriptor} instances
     * contained in this {@link GrpcRouting}
     */
    public List<GrpcServiceDescriptor> services() {
        return services;
    }

    GrpcRouteHandler<?, ?> findRoute(HttpPrologue prologue) {
        for (GrpcRoute route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route.handler(prologue);
            }
        }

        return null;
    }

    List<GrpcRoute> routes() {
        return routes;
    }

    /**
     * Fluent API builder for {@link GrpcRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, GrpcRouting> {
        private final List<GrpcRoute> routes = new LinkedList<>();
        private final WeightedBag<ServerInterceptor> interceptors = WeightedBag.create(InterceptorWeights.USER);
        private final Map<String, GrpcServiceDescriptor> services = new LinkedHashMap<>();

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
            routes.add(GrpcServiceRoute.create(service, interceptors));
            return this;
        }

        /**
         * Configure a bindable service.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(BindableService service) {
            routes.add(GrpcServiceRoute.create(service, interceptors));
            return this;
        }

        /**
         * Add all the routes for a {@link BindableService} service.
         *
         * @param proto the proto descriptor
         * @param service the {@link BindableService} to add routes for
         * @return updated builder
         */
        public Builder service(Descriptors.FileDescriptor proto, BindableService service) {
            for (ServerMethodDefinition<?, ?> method : service.bindService().getMethods()) {
                routes.add(GrpcRouteHandler.methodDefinition(method, proto, interceptors));
            }
            return this;
        }

        /**
         * Configure a service using a {@link io.grpc.ServiceDescriptor}.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(GrpcServiceDescriptor service) {
            String name = service.name();
            if (services.containsKey(name)) {
                throw new IllegalArgumentException("Attempted to register service name " + name + " multiple times");
            }
            services.put(name, service);
            WeightedBag<ServerInterceptor> routeInterceptors = WeightedBag.create();
            routeInterceptors.merge(service.interceptors());
            routeInterceptors.merge(interceptors);
            routes.add(GrpcServiceRoute.create(service, routeInterceptors));
            return this;
        }

        /**
         * Add all the routes for the {@link io.grpc.ServerServiceDefinition} service.
         *
         * @param service the {@link io.grpc.ServerServiceDefinition} to add routes for
         * @return updated builder
         */
        public Builder service(ServerServiceDefinition service) {
            for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
                routes.add(GrpcRouteHandler.methodDefinition(method, null, interceptors));
            }
            return this;
        }

        /**
         * Add one or more global {@link ServerInterceptor} instances that will intercept calls
         * to all services in the {@link GrpcRouting} built by this builder.
         * <p>
         * If the added interceptors are annotated with the {@link io.helidon.common.Weight}
         * or if they implemented the {@link io.helidon.common.Weighted} interface,
         * that value will be used to assign a weight to use when applying the interceptor
         * otherwise a weight of {@link InterceptorWeights#USER} will be used.
         *
         * @param interceptors one or more global {@link ServerInterceptor}s
         * @return this builder to allow fluent method chaining
         */
        public Builder intercept(ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors));
            return this;
        }

        /**
         * Add one or more global {@link ServerInterceptor} instances that will intercept calls
         * to all services in the {@link GrpcRouting} built by this builder.
         * <p>
         * The added interceptors will be applied using the specified weight.
         *
         * @param weight the weight to assign to the interceptors
         * @param interceptors one or more global {@link ServerInterceptor}s
         * @return this builder to allow fluent method chaining
         */
        public Builder intercept(int weight, ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors), weight);
            return this;
        }

        /**
         * Unary route.
         *
         * @param proto proto descriptor
         * @param serviceName service name
         * @param methodName method name
         * @param method method to handle this route
         * @param <ReqT> request type
         * @param <ResT> response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder unary(Descriptors.FileDescriptor proto,
                                          String serviceName,
                                          String methodName,
                                          ServerCalls.UnaryMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.unary(proto, serviceName, methodName, method, interceptors));
            return this;
        }

        /**
         * Bidirectional route.
         *
         * @param proto proto descriptor
         * @param serviceName service name
         * @param methodName method name
         * @param method method to handle this route
         * @param <ReqT> request type
         * @param <ResT> response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder bidi(Descriptors.FileDescriptor proto,
                                         String serviceName,
                                         String methodName,
                                         ServerCalls.BidiStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.bidi(proto, serviceName, methodName, method, interceptors));
            return this;
        }

        /**
         * Server streaming route.
         *
         * @param proto proto descriptor
         * @param serviceName service name
         * @param methodName method name
         * @param method method to handle this route
         * @param <ReqT> request type
         * @param <ResT> response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder serverStream(Descriptors.FileDescriptor proto,
                                                 String serviceName,
                                                 String methodName,
                                                 ServerCalls.ServerStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.serverStream(proto, serviceName, methodName, method, interceptors));
            return this;
        }

        /**
         * Client streaming route.
         *
         * @param proto proto descriptor
         * @param serviceName service name
         * @param methodName method name
         * @param method method to handle this route
         * @param <ReqT> request type
         * @param <ResT> response type
         * @return updated builder
         */
        public <ReqT, ResT> Builder clientStream(Descriptors.FileDescriptor proto,
                                                 String serviceName,
                                                 String methodName,
                                                 ServerCalls.ClientStreamingMethod<ReqT, ResT> method) {
            routes.add(GrpcRouteHandler.clientStream(proto, serviceName, methodName, method, interceptors));
            return this;
        }
    }
}

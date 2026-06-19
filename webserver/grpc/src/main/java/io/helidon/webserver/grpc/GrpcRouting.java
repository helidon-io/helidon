/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.ConfigBuilderSupport;
import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.service.registry.Services;
import io.helidon.webserver.Routing;
import io.helidon.webserver.grpc.spi.GrpcServerService;
import io.helidon.webserver.grpc.spi.GrpcServerServiceProvider;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

/**
 * gRPC specific routing.
 */
public class GrpcRouting implements Routing {
    private static final GrpcRouting EMPTY = GrpcRouting.builder()
            .config(Config.empty())
            .build();
    private static final String SERVER_PROTOCOL_CONFIG_KEY = "server.protocols." + GrpcProtocolProvider.CONFIG_NAME;

    private final ArrayList<GrpcRoute> routes;
    private final WeightedBag<ServerInterceptor> interceptors;
    private final ArrayList<GrpcServiceDescriptor> services;

    private GrpcRouting(List<GrpcRoute> routes,
                        WeightedBag<ServerInterceptor> interceptors,
                        Map<String, GrpcServiceDescriptor> services) {
        this.routes = new ArrayList<>(routes);
        this.interceptors = interceptors;
        this.services = new ArrayList<>(services.values());
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
        private final List<RouteRegistration> routeRegistrations = new LinkedList<>();
        private final WeightedBag<ServerInterceptor> interceptors = WeightedBag.create(InterceptorWeights.USER);
        private final Map<String, GrpcServiceDescriptor> services = new LinkedHashMap<>();
        private final Set<String> excludedServiceNames = new LinkedHashSet<>();

        private Config config;

        private Builder() {
        }

        @Override
        public GrpcRouting build() {
            Config routingConfig = config == null ? Services.get(Config.class) : config;
            WeightedBag<ServerInterceptor> configuredInterceptors = WeightedBag.create(InterceptorWeights.USER);
            List<GrpcRoute> routes = new LinkedList<>();

            configuredInterceptors.add(ContextSettingServerInterceptor.instance());
            Map<String, GrpcServerService> configuredServices = new LinkedHashMap<>();
            if (excludedServiceNames.isEmpty()) {
                if (routingConfig.key().isRoot()) {
                    // first use the undocumented config key (backward compatibility)
                    addGrpcConfigServices(routingConfig.get(SERVER_PROTOCOL_CONFIG_KEY), configuredServices);
                    // then the documented one, which should be the one that wins
                    addGrpcConfigServices(routingConfig.get(GrpcProtocolProvider.CONFIG_NAME), configuredServices);
                } else {
                    addGrpcConfigServices(routingConfig, configuredServices);
                }
            } else {
                if (routingConfig.key().isRoot()) {
                    // first use the undocumented config key (backward compatibility)
                    addGrpcConfigServices(routingConfig.get(SERVER_PROTOCOL_CONFIG_KEY),
                                          excludedServiceNames,
                                          configuredServices);
                    // then the documented one, which should be the one that wins
                    addGrpcConfigServices(routingConfig.get(GrpcProtocolProvider.CONFIG_NAME),
                                          excludedServiceNames,
                                          configuredServices);
                } else {
                    addGrpcConfigServices(routingConfig, excludedServiceNames, configuredServices);
                }
            }
            for (GrpcServerService serverService : configuredServices.values()) {
                configuredInterceptors.merge(serverService.interceptors());
            }
            WeightedBag<ServerInterceptor> routingInterceptors = configuredInterceptors.copyMe();
            routingInterceptors.merge(interceptors);
            routeRegistrations.forEach(registration -> registration.register(routes, routingInterceptors));
            return new GrpcRouting(routes, routingInterceptors, services);
        }

        private static void addGrpcConfigServices(Config config,
                                                  Map<String, GrpcServerService> configuredServices) {
            for (GrpcServerService serverService : GrpcConfig.create(config).grpcServices()) {
                configuredServices.put(serverService.name(), serverService);
            }
        }

        private static void addGrpcConfigServices(Config config,
                                                  Set<String> excludedServiceNames,
                                                  Map<String, GrpcServerService> configuredServices) {
            Set<String> providerTypes = new LinkedHashSet<>();
            HelidonServiceLoader.create(GrpcServerServiceProvider.class)
                    .forEach(provider -> providerTypes.add(provider.configKey()));
            Config grpcServices = config.get("grpc-services");
            List<GrpcServerService> ignoredServices = new ArrayList<>(excludedServiceNames.size() * 2);
            for (String excludedServiceName : excludedServiceNames) {
                ignoredServices.add(new ExcludedGrpcServerService(excludedServiceName, excludedServiceName));
                // Provider-key exclusions must not suppress a differently typed service with the same configured name.
                if (!providerTypes.contains(excludedServiceName)) {
                    if (grpcServices.isList()) {
                        for (Config serviceConfig : grpcServices.asNodeList().orElseGet(List::of)) {
                            String serviceType = serviceConfig.get("type").asString().orElse(null);
                            String serviceName = serviceConfig.get("name").asString().orElse(serviceType);
                            if (serviceType == null) {
                                List<Config> configs = serviceConfig.asNodeList().orElseGet(List::of);
                                if (configs.size() == 1) {
                                    Config usedConfig = configs.getFirst();
                                    serviceName = usedConfig.name();
                                    serviceType = usedConfig.get("type").asString().orElse(serviceName);
                                }
                            }
                            if (excludedServiceName.equals(serviceName)) {
                                ignoredServices.add(new ExcludedGrpcServerService(serviceType, serviceName));
                            }
                        }
                    } else {
                        String serviceType = grpcServices.get(excludedServiceName)
                                .get("type")
                                .asString()
                                .orElse(excludedServiceName);
                        ignoredServices.add(new ExcludedGrpcServerService(serviceType, excludedServiceName));
                    }
                }
            }

            boolean discoverServices = config.get("grpc-services-discover-services").asBoolean().orElse(false);
            for (GrpcServerService serverService : ConfigBuilderSupport.discoverServices(
                    config,
                    "grpc-services",
                    GrpcServerServiceProvider.class,
                    GrpcServerService.class,
                    discoverServices,
                    ignoredServices)) {
                configuredServices.put(serverService.name(), serverService);
            }
        }

        private record ExcludedGrpcServerService(String type, String name) implements GrpcServerService {
            @Override
            public WeightedBag<ServerInterceptor> interceptors() {
                return WeightedBag.create();
            }
        }

        /**
         * Configuration instance to use to configure this routing.
         *
         * @param config configuration to use
         * @return updated builder
         */
        public Builder config(Config config) {
            this.config = Objects.requireNonNull(config);
            return this;
        }

        /**
         * Configured gRPC server service names to exclude.
         * <p>
         * These named services will not be loaded through the standard gRPC server service provider configuration.
         *
         * @param excludedServiceNames configured gRPC server service names to exclude
         * @return updated builder
         */
        @Api.Internal
        public Builder excludedServiceNames(Set<String> excludedServiceNames) {
            Set<String> newExcludedServiceNames = new LinkedHashSet<>();
            Objects.requireNonNull(excludedServiceNames)
                    .forEach(name -> newExcludedServiceNames.add(Objects.requireNonNull(name)));
            this.excludedServiceNames.clear();
            this.excludedServiceNames.addAll(newExcludedServiceNames);
            return this;
        }

        /**
         * Add a configured gRPC server service name to exclude.
         * <p>
         * This named service will not be loaded through the standard gRPC server service provider configuration.
         *
         * @param excludedServiceName configured gRPC server service name to exclude
         * @return updated builder
         */
        @Api.Internal
        public Builder addExcludedServiceName(String excludedServiceName) {
            this.excludedServiceNames.add(Objects.requireNonNull(excludedServiceName));
            return this;
        }

        /**
         * Configure grpc service.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(GrpcService service) {
            GrpcService grpcService = Objects.requireNonNull(service);
            routeRegistrations.add((routes, interceptors) ->
                                           routes.add(GrpcServiceRoute.create(grpcService, interceptors)));
            return this;
        }

        /**
         * Configure a bindable service.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(BindableService service) {
            BindableService bindableService = Objects.requireNonNull(service);
            routeRegistrations.add((routes, interceptors) ->
                                           routes.add(GrpcServiceRoute.create(bindableService, interceptors)));
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
            Descriptors.FileDescriptor serviceProto = Objects.requireNonNull(proto);
            BindableService bindableService = Objects.requireNonNull(service);
            routeRegistrations.add((routes, interceptors) -> bindableService.bindService()
                    .getMethods()
                    .forEach(method -> routes.add(GrpcRouteHandler.methodDefinition(method,
                                                                                    serviceProto,
                                                                                    interceptors))));
            return this;
        }

        /**
         * Configure a service using a {@link io.grpc.ServiceDescriptor}.
         *
         * @param service service to add
         * @return updated builder
         */
        public Builder service(GrpcServiceDescriptor service) {
            GrpcServiceDescriptor serviceDescriptor = Objects.requireNonNull(service);
            String name = serviceDescriptor.fullName();
            if (services.containsKey(name)) {
                throw new IllegalArgumentException("Attempted to register service name " + name + " multiple times");
            }
            services.put(name, serviceDescriptor);

            WeightedBag<ServerInterceptor> serviceInterceptors = serviceDescriptor.interceptors();
            routeRegistrations.add((routes, interceptors) -> {
                WeightedBag<ServerInterceptor> routeInterceptors;
                if (!serviceInterceptors.isEmpty()) {
                    routeInterceptors = WeightedBag.create();
                    routeInterceptors.merge(serviceInterceptors);
                    routeInterceptors.merge(interceptors);
                } else {
                    routeInterceptors = interceptors;
                }
                routes.add(GrpcServiceRoute.create(serviceDescriptor, routeInterceptors));
            });
            return this;
        }

        /**
         * Add all the routes for the {@link io.grpc.ServerServiceDefinition} service.
         *
         * @param service the {@link io.grpc.ServerServiceDefinition} to add routes for
         * @return updated builder
         */
        public Builder service(ServerServiceDefinition service) {
            ServerServiceDefinition serviceDefinition = Objects.requireNonNull(service);
            routeRegistrations.add((routes, interceptors) -> serviceDefinition.getMethods()
                    .forEach(method -> routes.add(GrpcRouteHandler.methodDefinition(method, null, interceptors))));
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
            this.interceptors.addAll(Arrays.asList(Objects.requireNonNull(interceptors)));
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
            this.interceptors.addAll(Arrays.asList(Objects.requireNonNull(interceptors)), weight);
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
            Descriptors.FileDescriptor routeProto = Objects.requireNonNull(proto);
            String routeServiceName = Objects.requireNonNull(serviceName);
            String routeMethodName = Objects.requireNonNull(methodName);
            ServerCalls.UnaryMethod<ReqT, ResT> routeMethod = Objects.requireNonNull(method);
            routeRegistrations.add((routes, interceptors) ->
                                           routes.add(GrpcRouteHandler.unary(routeProto,
                                                                             routeServiceName,
                                                                             routeMethodName,
                                                                             routeMethod,
                                                                             interceptors)));
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
            Descriptors.FileDescriptor routeProto = Objects.requireNonNull(proto);
            String routeServiceName = Objects.requireNonNull(serviceName);
            String routeMethodName = Objects.requireNonNull(methodName);
            ServerCalls.BidiStreamingMethod<ReqT, ResT> routeMethod = Objects.requireNonNull(method);
            routeRegistrations.add((routes, interceptors) ->
                                           routes.add(GrpcRouteHandler.bidi(routeProto,
                                                                            routeServiceName,
                                                                            routeMethodName,
                                                                            routeMethod,
                                                                            interceptors)));
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
            Descriptors.FileDescriptor routeProto = Objects.requireNonNull(proto);
            String routeServiceName = Objects.requireNonNull(serviceName);
            String routeMethodName = Objects.requireNonNull(methodName);
            ServerCalls.ServerStreamingMethod<ReqT, ResT> routeMethod = Objects.requireNonNull(method);
            routeRegistrations.add((routes, interceptors) ->
                                           routes.add(GrpcRouteHandler.serverStream(routeProto,
                                                                                    routeServiceName,
                                                                                    routeMethodName,
                                                                                    routeMethod,
                                                                                    interceptors)));
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
            Descriptors.FileDescriptor routeProto = Objects.requireNonNull(proto);
            String routeServiceName = Objects.requireNonNull(serviceName);
            String routeMethodName = Objects.requireNonNull(methodName);
            ServerCalls.ClientStreamingMethod<ReqT, ResT> routeMethod = Objects.requireNonNull(method);
            routeRegistrations.add((routes, interceptors) ->
                                           routes.add(GrpcRouteHandler.clientStream(routeProto,
                                                                                    routeServiceName,
                                                                                    routeMethodName,
                                                                                    routeMethod,
                                                                                    interceptors)));
            return this;
        }

        private interface RouteRegistration {
            void register(List<GrpcRoute> routes, WeightedBag<ServerInterceptor> interceptors);
        }
    }
}

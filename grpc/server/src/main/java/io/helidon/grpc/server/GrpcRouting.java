/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;

/**
 * GrpcRouting represents the composition of gRPC services with interceptors and routing rules.
 *
 * It is together with {@link GrpcServerConfiguration.Builder} a cornerstone of the {@link GrpcServer}.
 */
public interface GrpcRouting {

    /**
     * Obtain a {@link List} of the {@link ServiceDescriptor} instances
     * contained in this {@link GrpcRouting}.
     *
     * @return a {@link List} of the {@link ServiceDescriptor} instances
     *         contained in this {@link GrpcRouting}
     */
    List<ServiceDescriptor> services();

    /**
     * Obtain a {@link List} of the global {@link io.grpc.ServerInterceptor interceptors}
     * that should be applied to all services.
     *
     * @return a {@link List} of the global {@link io.grpc.ServerInterceptor interceptors}
     *         that should be applied to all services
     */
    PriorityBag<ServerInterceptor> interceptors();

    /**
     * Obtain a GrpcRouting builder.
     *
     * @return a GrpcRouting builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates new {@link GrpcServer} instance with provided configuration and this routing.
     *
     * @param configuration a gRPC server configuration
     * @return new {@link GrpcServer} instance
     * @throws IllegalStateException if none SPI implementation found
     */
    default GrpcServer createServer(GrpcServerConfiguration configuration) {
        return GrpcServer.create(configuration, this);
    }

    /**
     * Creates new {@link GrpcServer} instance with this routing and default configuration.
     *
     * @return new {@link GrpcServer} instance
     * @throws IllegalStateException if none SPI implementation found
     */
    default GrpcServer createServer() {
        return GrpcServer.create(this);
    }

    /**
     * A builder that can build {@link GrpcRouting} instances.
     */
    final class Builder implements io.helidon.common.Builder<GrpcRouting> {

        /**
         * The {@link Map} of the {@link ServiceDescriptor} instances
         * to add to the {@link GrpcRouting} keyed by service name.
         */
        private Map<String, ServiceDescriptor> services = new LinkedHashMap<>();

        /**
         * The {@link List} of the global {@link io.grpc.ServerInterceptor}s that should be
         * applied to all services.
         */
        private PriorityBag<ServerInterceptor> interceptors = PriorityBag.withDefaultPriority(InterceptorPriorities.USER);

        /**
         * Add one or more global {@link ServerInterceptor} instances that will intercept calls
         * to all services in the {@link GrpcRouting} built by this builder.
         * <p>
         * If the added interceptors are annotated with the {@link javax.annotation.Priority}
         * annotation then that value will be used to assign a priority to use when applying
         * the interceptor otherwise a priority of {@link InterceptorPriorities#USER} will
         * be used.
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
         * The added interceptors will be applied using the specified priority.
         *
         * @param priority the priority to assign to the interceptors
         * @param interceptors one or more global {@link ServerInterceptor}s
         * @return this builder to allow fluent method chaining
         */
        public Builder intercept(int priority, ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors), priority);
            return this;
        }

        /**
         * Add a {@link GrpcService} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service  the {@link GrpcService} to register
         * @return this builder to allow fluent method chaining
         * @throws java.lang.IllegalArgumentException if an attempt is made to register a service
         *         with the same name as an already registered service
         */
        public Builder register(GrpcService service) {
            return register(service, null);
        }

        /**
         * Add a {@link GrpcService} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service    the {@link GrpcService} to register
         * @param configurer an optional configurer that can update the {@link ServiceDescriptor}
         *                   for the registered service
         * @return this builder to allow fluent method chaining
         * @throws java.lang.IllegalArgumentException if an attempt is made to register a service
         *         with the same name as an already registered service
         */
        public Builder register(GrpcService service, ServiceDescriptor.Configurer configurer) {
            return register(ServiceDescriptor.builder(service), configurer);
        }

        /**
         * Add a {@link BindableService} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service  the {@link BindableService} to register
         * @return this builder to allow fluent method chaining
         * @throws java.lang.IllegalArgumentException if an attempt is made to register a service
         *         with the same name as an already registered service
         */
        public Builder register(BindableService service) {
            return register(service, null);
        }

        /**
         * Add a {@link BindableService} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service    the {@link BindableService} to register
         * @param configurer an optional configurer that can update the {@link ServiceDescriptor}
         *                   for the registered service
         * @return this builder to allow fluent method chaining
         * @throws java.lang.IllegalArgumentException if an attempt is made to register a service
         *         with the same name as an already registered service
         */
        public Builder register(BindableService service, ServiceDescriptor.Configurer configurer) {
            return register(ServiceDescriptor.builder(service), configurer);
        }

        /**
         * Register a {@link ServiceDescriptor} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service  the {@link ServiceDescriptor} to register
         * @return this builder to allow fluent method chaining
         * @throws java.lang.IllegalArgumentException if an attempt is made to register a service
         *         with the same name as an already registered service
         */
        public Builder register(ServiceDescriptor service) {
            String name = service.name();
            if (services.containsKey(name)) {
                throw new IllegalArgumentException("Attempted to register service name " + name + " multiple times");
            }
            services.put(name, service);
            return this;
        }

        /**
         * Builds a new {@link GrpcRouting}.
         *
         * @return a new {@link GrpcRouting} instance
         */
        public GrpcRouting build() {
            return GrpcRoutingImpl.create(services.values(), interceptors);
        }

        // ---- helpers -----------------------------------------------------

        private Builder register(ServiceDescriptor.Builder builder,
                                 ServiceDescriptor.Configurer configurer) {
            if (configurer != null) {
                configurer.configure(builder);
            }

            interceptors.stream()
                    .filter(interceptor -> ServiceDescriptor.Configurer.class.isAssignableFrom(interceptor.getClass()))
                    .map(ServiceDescriptor.Configurer.class::cast)
                    .forEach(interceptor -> interceptor.configure(builder));

            register(builder.build());
            return this;
        }
    }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    List<ServerInterceptor> interceptors();

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
     * A {@link io.helidon.common.Builder} that can build {@link GrpcRouting} instances.
     */
    final class Builder implements io.helidon.common.Builder<GrpcRouting> {

        /**
         * The {@link List} of the {@link ServiceDescriptor} instances
         * to add to the {@link GrpcRouting}.
         */
        private List<ServiceDescriptor> services = new ArrayList<>();

        /**
         * The {@link List} of the global {@link io.grpc.ServerInterceptor}s that should be
         * applied to all services.
         */
        private List<ServerInterceptor> interceptors = new ArrayList<>();

        /**
         * Add one or more global {@link ServerInterceptor} instances that will intercept calls
         * to all services in the {@link GrpcRouting} built by this builder.
         *
         * @param interceptors one or more global {@link ServerInterceptor}s
         * @return this builder to allow fluent method chaining
         */
        public Builder intercept(ServerInterceptor... interceptors) {
            Collections.addAll(this.interceptors, Objects.requireNonNull(interceptors));
            return this;
        }

        /**
         * Add a {@link GrpcService} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service the {@link GrpcService} to register
         * @return this builder to allow fluent method chaining
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
         */
        public Builder register(GrpcService service, ServiceDescriptor.Configurer configurer) {
            return register(ServiceDescriptor.builder(service), configurer);
        }

        /**
         * Add a {@link BindableService} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service    the {@link BindableService} to register
         * @return this builder to allow fluent method chaining
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
         */
        public Builder register(BindableService service, ServiceDescriptor.Configurer configurer) {
            return register(ServiceDescriptor.builder(service), configurer);
        }

        /**
         * Add a {@link ServiceDescriptor} with the {@link GrpcRouting} to be built by this builder.
         *
         * @param service    the {@link ServiceDescriptor} to register
         * @return this builder to allow fluent method chaining
         */
        public Builder register(ServiceDescriptor service) {
            services.add(service);
            return this;
        }

        /**
         * Builds a new {@link GrpcRouting}.
         *
         * @return a new {@link GrpcRouting} instance
         */
        public GrpcRouting build() {
            return new GrpcRoutingImpl(services, interceptors);
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

            services.add(builder.build());
            return this;
        }
    }
}

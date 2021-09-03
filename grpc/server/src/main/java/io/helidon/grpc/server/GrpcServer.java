/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.ServerInterceptor;
import io.opentracing.Tracer;
import org.eclipse.microprofile.health.HealthCheck;

/**
 * Represents a immutably configured gRPC server.
 * <p>
 * Provides a basic lifecycle and monitoring API.
 * <p>
 * Instance can be created from {@link GrpcRouting} and optionally from {@link
 * GrpcServerConfiguration} using {@link #create(GrpcRouting)}, {@link
 * #create(GrpcServerConfiguration, GrpcRouting)} or {@link #builder(GrpcRouting)} methods
 * and their builder enabled overloads.
 */
public interface GrpcServer {
    /**
     * Gets effective server configuration.
     *
     * @return Server configuration
     */
    GrpcServerConfiguration configuration();

    /**
     * Gets a {@link GrpcServer} context.
     *
     * @return a server context
     */
    Context context();

    /**
     * Starts the server. Has no effect if server is running.
     *
     * @return a completion stage of starting process
     */
    CompletionStage<GrpcServer> start();

    /**
     * Completion stage is completed when server is shut down.
     *
     * @return a completion stage of the server
     */
    CompletionStage<GrpcServer> whenShutdown();

    /**
     * Attempt to gracefully shutdown server. It is possible to use returned
     * {@link CompletionStage} to react.
     * <p>
     * RequestMethod can be called periodically.
     *
     * @return to react on finished shutdown process
     * @see #start()
     */
    CompletionStage<GrpcServer> shutdown();

    /**
     * Return an array of health checks for this server.
     *
     * @return an array of {@link HealthCheck} instances for this server
     */
    HealthCheck[] healthChecks();

    /**
     * Obtain the deployed services.
     *
     * @return an immutable {@link Map} of deployed {@link ServiceDescriptor}s
     *         keyed by service name
     */
    Map<String, ServiceDescriptor> services();

    /**
     * Returns {@code true} if the server is currently running. A running server
     * in the stopping phase returns {@code true} until it is fully stopped.
     *
     * @return {@code true} if server is running
     */
    boolean isRunning();

    /**
     * Returns a port number the default server socket is bound to and is
     * listening on; or {@code -1} if unknown or not active.
     * <p>
     * Only supported only when server is running.
     *
     * @return a listen port; or {@code -1} if unknown or the default server
     *         socket is not active
     */
    int port();

    /**
     * Creates a new instance from a provided configuration and a GrpcRouting.
     *
     * @param configurationBuilder a server configuration builder that will be
     *                             built as a first step of this method
     *                             execution; may be {@code null}
     * @param routing              a GrpcRouting instance
     *
     * @return a new gRPC server instance
     *
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'GrpcRouting' parameter is {@code null}
     */
    static GrpcServer create(Supplier<? extends GrpcServerConfiguration> configurationBuilder, GrpcRouting routing) {
        return create(configurationBuilder != null
                              ? configurationBuilder.get()
                              : null, routing);
    }

    /**
     * Creates new instance form provided configuration and GrpcRouting.
     *
     * @param configurationBuilder a server configuration builder that will be
     *                             built as a first step of this method
     *                             execution; may be {@code null}
     * @param routingBuilder       a GrpcRouting builder that will be built as a
     *                             second step of this method execution
     *
     * @return a new gRPC server instance
     *
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routingBuilder' parameter is {@code
     *                               null}
     */
    static GrpcServer create(Supplier<? extends GrpcServerConfiguration> configurationBuilder,
                             Supplier<? extends GrpcRouting> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(configurationBuilder != null
                              ? configurationBuilder.get()
                              : null, routingBuilder.get());
    }

    /**
     * Creates new instance form provided configuration and GrpcRouting.
     *
     * @param configuration  a server configuration instance
     * @param routingBuilder a GrpcRouting builder that will be built as a second
     *                       step of this method execution
     * @return a new gRPC server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routingBuilder' parameter is {@code
     *                               null}
     */
    static GrpcServer create(
            GrpcServerConfiguration configuration,
            Supplier<? extends GrpcRouting> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(configuration, routingBuilder.get());
    }

    /**
     * Creates new instance form provided GrpcRouting and default configuration.
     *
     * @param routing a GrpcRouting instance
     * @return a new gRPC server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     */
    static GrpcServer create(GrpcRouting routing) {
        return create((GrpcServerConfiguration) null, routing);
    }

    /**
     * Creates new instance form provided configuration and GrpcRouting.
     *
     * @param configuration a server configuration instance
     * @param routing       a GrpcRouting instance
     * @return a new gRPC server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'GrpcRouting' parameter is {@code null}
     */
    static GrpcServer create(GrpcServerConfiguration configuration, GrpcRouting routing) {
        Objects.requireNonNull(routing, "Parameter 'routing' is null!");

        return builder(routing)
                .config(configuration)
                .build();
    }

    /**
     * Creates new instance form provided GrpcRouting and default configuration.
     *
     * @param routingBuilder a GrpcRouting builder instance that will be built as a
     *                       first step of this method execution
     * @return a new gRPC server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'GrpcRouting' parameter is {@code null}
     */
    static GrpcServer create(Supplier<? extends GrpcRouting> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(routingBuilder.get());
    }

    /**
     * Creates a builder of the {@link GrpcServer}.
     *
     * @param routingBuilder the GrpcRouting builder; must not be {@code null}
     * @return the builder
     */
    static Builder builder(Supplier<? extends GrpcRouting> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return builder(routingBuilder.get());
    }

    /**
     * Creates a builder of the {@link GrpcServer}.
     *
     * @param routing the GrpcRouting; must not be {@code null}
     * @return the builder
     */
    static Builder builder(GrpcRouting routing) {
        return new Builder(GrpcServerConfiguration.create(), routing);
    }

    /**
     * GrpcServer builder class provides a convenient way to timed a
     * GrpcServer instance.
     */
    final class Builder
            implements io.helidon.common.Builder<GrpcServer> {

        private final GrpcRouting routing;

        private GrpcServerConfiguration configuration;

        private Builder(GrpcServerConfiguration configuration, GrpcRouting routing) {
            Objects.requireNonNull(configuration, "Parameter 'configuration' must not be null!");
            Objects.requireNonNull(routing, "Parameter 'routing' must not be null!");

            this.configuration = configuration;
            this.routing = routing;
        }

        /**
         * Set a configuration of the {@link GrpcServer}.
         *
         * @param configuration the configuration
         * @return an updated builder
         */
        public Builder config(GrpcServerConfiguration configuration) {
            this.configuration = configuration != null ? configuration : GrpcServerConfiguration.create();
            return this;
        }

        /**
         * Set a configuration of the {@link GrpcServer}.
         *
         * @param configurationBuilder the configuration builder
         * @return an updated builder
         */
        public Builder config(Supplier<GrpcServerConfiguration> configurationBuilder) {
            this.configuration = configurationBuilder != null
                    ? configurationBuilder.get()
                    : GrpcServerConfiguration.create();
            return this;
        }

        /**
         * Builds the {@link GrpcServer} instance as configured by this builder
         * and its parameters.
         *
         * @return a ready to use {@link GrpcServer}
         */
        @Override
        public GrpcServer build() {
            PriorityBag<ServerInterceptor> interceptors = PriorityBag.create();
            GrpcServerImpl server = GrpcServerImpl.create(configuration);

            interceptors.add(ContextSettingServerInterceptor.create());

            Tracer tracer = configuration.tracer();
            if (tracer != null) {
                interceptors.add(GrpcTracing.create(tracer, configuration.tracingConfig()));
            }

            // add the global interceptors from the routing AFTER the tracing interceptor
            // so that all of those interceptors are included in the trace timings
            interceptors.merge(routing.interceptors());

            for (ServiceDescriptor service : routing.services()) {
                server.deploy(service, interceptors);
            }

            return server;
        }
    }
}

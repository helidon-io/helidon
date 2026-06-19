/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.service.registry.Service;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.Protocol;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;

/**
 * gRPC client.
 */
public interface GrpcClient extends RuntimeType.Api<GrpcClientConfig> {
    /**
     * Protocol ID constant for gRPC.
     */
    String PROTOCOL_ID = "grpc";

    /**
     * Protocol to use to obtain an instance of gRPC specific client from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     */
    Protocol<GrpcClient, GrpcClientProtocolConfig> PROTOCOL = GrpcProtocolProvider::new;

    /**
     * A new fluent API builder to customize client setup.
     *
     * @return a new builder
     */
    static GrpcClientConfig.Builder builder() {
        return GrpcClientConfig.builder();
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param clientConfig HTTP/2 client configuration
     * @return a new HTTP/2 client
     */
    static GrpcClient create(GrpcClientConfig clientConfig) {
        return new GrpcClientImpl(WebClient.create(it -> it.from(clientConfig)), clientConfig);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer HTTP/2 client configuration
     * @return a new HTTP/2 client
     */
    static GrpcClient create(Consumer<GrpcClientConfig.Builder> consumer) {
        return create(GrpcClientConfig.builder()
                .update(consumer)
                .buildPrototype());
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return a new HTTP/2 client
     */
    static GrpcClient create() {
        return create(GrpcClientConfig.create());
    }

    /**
     * Create a client for a specific service. The client will be backed by the same HTTP/2 client.
     *
     * @param descriptor descriptor to use
     * @return client for the provided descriptor
     */
    GrpcServiceClient serviceClient(GrpcServiceDescriptor descriptor);

    /**
     * Create a gRPC channel for this client that can be used to create stubs.
     *
     * @return a new gRPC channel
     */
    Channel channel();

    /**
     * Create a gRPC channel for this client that can be used to create stubs.
     *
     * @param interceptors the array of client interceptors
     * @return a new gRPC channel
     */
    Channel channel(ClientInterceptor... interceptors);

    /**
     * Create a gRPC channel for this client that can be used to create stubs.
     *
     * @param interceptors the list of client interceptors
     * @return a new gRPC channel
     */
    default Channel channel(Collection<ClientInterceptor> interceptors) {
        return channel(interceptors.toArray(new ClientInterceptor[]{}));
    }

    /**
     * Configuration for this gRPC client.
     *
     * @return the configuration
     */
    GrpcClientConfig clientConfig();

    /**
     * Defines a declarative gRPC client endpoint.
     * <p>
     * Configuration options for gRPC clients (prefixed by {@link #configKey()}):
     * <table class="config">
     *    <caption>gRPC Client Configuration Options</caption>
     *    <tr>
     *      <th>Key</th>
     *      <th>Default Value</th>
     *      <th>Description</th>
     *    </tr>
     *    <tr>
     *      <th>{@code client}</th>
     *      <th>&nbsp;</th>
     *      <th>Backing {@link GrpcClient} configuration. The endpoint URI is applied after this configuration.</th>
     *    </tr>
     * </table>
     *
     * In case key {@code client} node exists under the configuration node of this API, a new client will be created for
     * this instance, using {@link #value()} as its base URI (this always wins).
     * In case the {@link #clientName()} is defined, and an instance of that name is available in registry, it will be
     * used for this instance. If the named client is not available, a new client will be created with {@link #value()}
     * as its base URI.
     * When {@link #clientName()} is not defined, we use an unnamed client instance from the registry (if any).
     * The last resort is to create a new client with {@link #value()} as its base URI.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @interface Endpoint {
        /**
         * Target URI of the generated backing gRPC client.
         * <p>
         * Supports configuration references, such as {@code "http://localhost:${server.port}"}.
         * This value is used as the base URI when the generated client creates a backing {@link GrpcClient}.
         * Registry-provided clients keep their own base URI.
         *
         * @return target URI
         */
        String value();

        /**
         * Configuration key base to use when looking up options for the backing gRPC client.
         *
         * @return configuration key prefix
         */
        String configKey() default "";

        /**
         * Name of a registry-provided {@link GrpcClient} to use.
         *
         * @return client name
         */
        String clientName() default "";
    }

    /**
     * Qualifier for generated declarative gRPC client implementations.
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    @Documented
    @Service.Qualifier
    @interface Client {
    }
}

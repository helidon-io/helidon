/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.client;

import java.util.Optional;

import io.helidon.config.objectmapping.Value;
import io.helidon.grpc.core.GrpcTlsDescriptor;

import io.grpc.NameResolver;

/**
 * GrpcChannelDescriptor contains the configuration for a {@link io.grpc.Channel}.
 */
public class GrpcChannelDescriptor {
    private boolean inProcessChannel;
    private String host;
    private int port;
    private String target;
    private GrpcTlsDescriptor tlsDescriptor;
    private String loadBalancerPolicy;
    private NameResolver.Factory nameResolver;

    private GrpcChannelDescriptor(Builder builder) {
        this.inProcessChannel = builder.inProcessChannel();
        this.target = builder.target();
        this.host = builder.host();
        this.port = builder.port();
        this.tlsDescriptor = builder.tlsDescriptor();
        this.loadBalancerPolicy = builder.loadBalancerPolicy();
        this.nameResolver = builder.nameResolverFactory();
    }

    /**
     * Create and return a new {@link Builder}.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this is a descriptor for building a in process {@link io.grpc.Channel}.
     * @return true if this is a descriptor for building a in process {@link io.grpc.Channel}
     */
    public boolean isInProcessChannel() {
        return inProcessChannel;
    }

    /**
     * Get the host name to connect.
     *
     * @return the host name to connect
     */
    public String host() {
        return host;
    }

    /**
     * Get the port that will be used to connect to the server.
     *
     * @return the port that will be used to connect to the server
     */
    public int port() {
        return port;
    }

    /**
     * Get the optional target string to use to resolve channel addresses.
     *
     * @return the optional target string to use to resolve channel addresses
     */
    public Optional<String> target() {
        return Optional.ofNullable(target);
    }

    /**
     * Get the default load balancer policy to use.
     *
     * @return the optional default load balancer policy to use
     */
    public Optional<String> loadBalancerPolicy() {
        return Optional.ofNullable(loadBalancerPolicy);
    }

    /**
     * Get the {@link NameResolver.Factory} to use.
     *
     * @return the optional {@link NameResolver.Factory} to use
     */
    public Optional<NameResolver.Factory> nameResolverFactory() {
        return Optional.ofNullable(nameResolver);
    }

    /**
     * Get the {@link io.helidon.grpc.core.GrpcTlsDescriptor}. If this method returns null or
     * if {@code tlsDescriptor.isEnabled()} is false, then no TLS will be used (and none of the other configuration
     * values from {@code tlsDescriptor} will be used).
     * <p>
     * If the {@link GrpcTlsDescriptor} has been set but the value of {@link io.helidon.grpc.core.GrpcTlsDescriptor#isEnabled()}
     * returns {@code false} then an empty {@link Optional} will be returned.
     *
     * @return the optional {@link io.helidon.grpc.core.GrpcTlsDescriptor}
     */
    public Optional<GrpcTlsDescriptor> tlsDescriptor() {
        if (tlsDescriptor != null && tlsDescriptor.isEnabled()) {
            return Optional.of(tlsDescriptor);
        }
        return Optional.empty();
    }

    /**
     * Builder builds a GrpcChannelDescriptor.
     */
    public static class Builder implements io.helidon.common.Builder<GrpcChannelDescriptor> {
        private boolean inProcessChannel;
        private String host = GrpcChannelsProvider.DEFAULT_HOST;
        private int port = GrpcChannelsProvider.DEFAULT_PORT;
        private GrpcTlsDescriptor tlsDescriptor;
        private String target;
        private String loadBalancerPolicy;
        private NameResolver.Factory nameResolver;

        /**
         * Set the host name to connect.
         *
         * @return this instance for fluent API
         */
        @Value(key = "inProcess", withDefault = GrpcChannelsProvider.DEFAULT_HOST)
        public Builder inProcess() {
            this.inProcessChannel = true;
            return this;
        }

        /**
         * Set the target string, which can be either a valid {@link io.grpc.NameResolver}
         * compliant URI, or an authority string.
         *
         * @param target the target string
         *
         * @return this instance for fluent API
         *
         * @see io.grpc.ManagedChannelBuilder#forTarget(String)
         */
        @Value
        public Builder target(String target) {
            this.target = target;
            return this;
        }

        /**
         * Set the host name to connect.
         * @param host set the host name
         *
         * @return this instance for fluent API
         */
        @Value(withDefault = GrpcChannelsProvider.DEFAULT_HOST)
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Set the port that will be used to connect to the server.
         * @param port the port that will be used to connect to the server
         *
         * @return this instance for fluent API
         */
        @Value(withDefault = "" + GrpcChannelsProvider.DEFAULT_PORT)
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Set the GrpcTlsDescriptor. If {@code tlsDescriptor} is null or if the {@code tlsDescriptor.isEnabled()} is false,
         * then no TLS will be used.
         * @param tlsDescriptor the GrpcSslDescriptor
         *
         * @return this instance for fluent API
         */
        @Value(key = "tls")
        public Builder sslDescriptor(GrpcTlsDescriptor tlsDescriptor) {
            this.tlsDescriptor = tlsDescriptor;
            return this;
        }

        /**
         * Set the default load balancer policy name.
         * @param policy the load balancer policy name
         *
         * @return this instance for fluent API
         *
         * @see io.grpc.ManagedChannelBuilder#defaultLoadBalancingPolicy(String)
         */
        public Builder loadBalancerPolicy(String policy) {
            loadBalancerPolicy = policy;
            return this;
        }

        /**
         * Set the {@link io.grpc.NameResolver.Factory} to use.
         * @param factory the {@link io.grpc.NameResolver.Factory} to use
         *
         * @return this instance for fluent API
         *
         * @see io.grpc.ManagedChannelBuilder#nameResolverFactory(io.grpc.NameResolver.Factory)
         */
        public Builder nameResolverFactory(NameResolver.Factory factory) {
            this.nameResolver = factory;
            return this;
        }

        boolean inProcessChannel() {
            return inProcessChannel;
        }

        String host() {
            return host;
        }

        int port() {
            return port;
        }

        GrpcTlsDescriptor tlsDescriptor() {
            return tlsDescriptor;
        }

        String target() {
            return target;
        }

        String loadBalancerPolicy() {
            return loadBalancerPolicy;
        }

        NameResolver.Factory nameResolverFactory() {
            return nameResolver;
        }

        /**
         * Build and return a new GrpcChannelDescriptor.
         * @return a new GrpcChannelDescriptor
         */
        public GrpcChannelDescriptor build() {
            return new GrpcChannelDescriptor(this);
        }
    }
}

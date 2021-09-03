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

package io.helidon.grpc.client;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.grpc.core.GrpcTlsDescriptor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * GrpcChannelsProvider is a factory for pre-configured gRPC Channel instances.
 */
public class GrpcChannelsProvider {

    /**
     * A constant for holding the default channel configuration name (which is "default").
     */
    public static final String DEFAULT_CHANNEL_NAME = "default";

    /**
     * A constant for holding the default host name (which is "localhost").
     */
    public static final String DEFAULT_HOST = "localhost";

    /**
     * The configuration key for the channels configuration.
     */
    public static final String CFG_KEY_CHANNELS = "channels";

    /**
     * A constant for holding the default port (which is "1408").
     */
    public static final int DEFAULT_PORT = 1408;

    private Map<String, GrpcChannelDescriptor> channelConfigs;

    private GrpcChannelsProvider(Map<String, GrpcChannelDescriptor> channelDescriptors) {
        this.channelConfigs = new HashMap<>(channelDescriptors);
    }

    /**
     * Builds a new instance of {@link GrpcChannelsProvider} using default configuration. The
     * default configuration connects to "localhost:1408" without TLS.
     *
     * @return a new instance of {@link GrpcChannelsProvider}
     */
    public static GrpcChannelsProvider create() {
        return GrpcChannelsProvider.builder().build();
    }

    /**
     * Creates a {@link GrpcChannelsProvider} using the specified configuration.
     *
     * @param config The externalized configuration.
     * @return a new instance of {@link GrpcChannelsProvider}
     */
    public static GrpcChannelsProvider create(Config config) {
        return new Builder(config).build();
    }

    /**
     * Create a new {@link GrpcChannelsProvider.Builder}.
     *
     * @return a new {@link GrpcChannelsProvider.Builder}
     */
    public static GrpcChannelsProvider.Builder builder() {
        return builder(null);
    }

    /**
     * Create a new {@link GrpcChannelsProvider.Builder}.
     *
     * @param config the {@link io.helidon.config.Config} to bootstrap from
     * @return a new {@link GrpcChannelsProvider.Builder}
     */
    public static GrpcChannelsProvider.Builder builder(Config config) {
        return new Builder(config);
    }

    private static SslContext createClientSslContext(Resource trustCert,
                                                     Resource clientCert,
                                                     Resource clientPrivateKey) {
        try {
            SslContextBuilder builder = GrpcSslContexts.forClient();
            if (trustCert != null) {
                builder.trustManager(trustCert.stream());
            }

            if (clientCert != null && clientPrivateKey != null) {
                builder.keyManager(clientCert.stream(), clientPrivateKey.stream());
            }

            return builder.build();
        } catch (SSLException e) {
            throw new RuntimeException("Error creating SSL context", e);
        }
    }

    // --------------- private methods of GrpcChannelsProvider ---------------

    /**
     * Returns a {@link io.grpc.ManagedChannel} for the specified channel or host name.
     * <p>
     * If the specified channel name does not exist in the configuration, we will assume
     * that it represents the name of the gRPC host to connect to and will create a plain text
     * channel to the host with the specified {@code name}, on a default port (1408).
     *
     * @param name the name of the channel configuration as specified in the configuration file,
     *             or the name of the host to connect to
     * @return a new instance of {@link io.grpc.ManagedChannel}
     * @throws NullPointerException if name is null
     * @throws IllegalArgumentException if name is empty
     */
    public ManagedChannel channel(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null.");
        }
        if (name.trim().length() == 0) {
            throw new IllegalArgumentException("name cannot be empty or blank.");
        }

        GrpcChannelDescriptor chCfg = channelConfigs.computeIfAbsent(name, hostName ->
                GrpcChannelDescriptor.builder().host(name).build());

        return createChannel(chCfg);
    }

    Map<String, GrpcChannelDescriptor> channels() {
        return channelConfigs;
    }

    private ManagedChannel createChannel(GrpcChannelDescriptor descriptor) {
        ManagedChannelBuilder<?> builder = descriptor.tlsDescriptor()
                .map(tlsDescriptor -> createNettyChannelBuilder(descriptor, tlsDescriptor))
                .orElse(createManagedChannelBuilder(descriptor));

        descriptor.loadBalancerPolicy().ifPresent(builder::defaultLoadBalancingPolicy);
        descriptor.nameResolverFactory().ifPresent(builder::nameResolverFactory);

        return builder.build();
    }

    /**
     * Create a TLS enabled {@link ManagedChannelBuilder}.
     *
     * @param descriptor the {@link GrpcChannelDescriptor} to use to configure the builder
     * @return a plain (non-TLS) ManagedChannelBuilder
     */
    @SuppressWarnings("rawtypes")
    private ManagedChannelBuilder createNettyChannelBuilder(GrpcChannelDescriptor descriptor,
                                                               GrpcTlsDescriptor tlsDescriptor) {
        return descriptor.target()
                         .map(NettyChannelBuilder::forTarget)
                         .orElse(NettyChannelBuilder.forAddress(descriptor.host(), descriptor.port()))
                         .negotiationType(NegotiationType.TLS)
                         .sslContext(createClientSslContext(tlsDescriptor.tlsCaCert(),
                                                            tlsDescriptor.tlsCert(),
                                                            tlsDescriptor.tlsKey()));
    }

    /**
     * Create a plain (non-TLS) {@link ManagedChannelBuilder}.
     *
     * @param descriptor the {@link GrpcChannelDescriptor} to use to configure the builder
     * @return a plain (non-TLS) ManagedChannelBuilder
     */
    @SuppressWarnings("rawtypes")
    private ManagedChannelBuilder createManagedChannelBuilder(GrpcChannelDescriptor descriptor) {
        return descriptor.target()
                         .map(ManagedChannelBuilder::forTarget)
                         .orElse(ManagedChannelBuilder.forAddress(descriptor.host(), descriptor.port()))
                         .usePlaintext();
    }

    /**
     * Builder builds an instance of {@link GrpcChannelsProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<GrpcChannelsProvider> {

        private Map<String, GrpcChannelDescriptor> channelConfigs = new HashMap<>();

        private Builder(Config config) {

            // Add the default channel (which can be overridden in the config)
            channel(DEFAULT_CHANNEL_NAME, GrpcChannelDescriptor.builder().build());

            if (config == null) {
                return;
            }

            Config channelsConfig = config.get(CFG_KEY_CHANNELS);

            if (channelsConfig.exists()) {
                for (Config channelConfig : channelsConfig.asNodeList().get()) {
                    String key = channelConfig.key().name();
                    GrpcChannelDescriptor cfg = channelConfig.asNode().get().as(GrpcChannelDescriptor.class).get();
                    channelConfigs.put(key, cfg);
                }
            }
        }

        /**
         * Add or replace the specified {@link GrpcChannelDescriptor}.
         *
         * @param name       the name of the configuration
         * @param descriptor the {@link GrpcChannelDescriptor} to be added
         * @return this Builder instance
         */
        public Builder channel(String name, GrpcChannelDescriptor descriptor) {
            channelConfigs.put(name, descriptor);
            return this;
        }

        /**
         * Create a new instance of {@link GrpcChannelsProvider} from this Builder.
         *
         * @return a new instance of {@link GrpcChannelsProvider}
         */
        public GrpcChannelsProvider build() {
            return new GrpcChannelsProvider(channelConfigs);
        }
    }

}

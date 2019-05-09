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

package io.helidon.grpc.client;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import io.helidon.config.Config;

import io.grpc.Channel;
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
     * A constant for holding the default port (which is "1408").
     */
    public static final int DEFAULT_PORT = 1408;

    private Map<String, GrpcChannelDescriptor> channelConfigs;

    private GrpcChannelsProvider(Map<String, GrpcChannelDescriptor> channelConfigs) {
        this.channelConfigs = new HashMap<>(channelConfigs);
    }

    /**
     * Builds a new instance of {@link GrpcChannelsProvider} using default configuration. The
     * default configuration connects to "localhost:1408" with no ssl.
     *
     * @return a new instance of {@link GrpcChannelsProvider}
     */
    public static GrpcChannelsProvider create() {
        return GrpcChannelsProvider.builder().build();
    }

    /**
     * u
     * Builds a new instance of {@link GrpcChannelsProvider} using the specified configuration.
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

    private static SslContext createClientSslContext(String trustCertCollectionFilePath,
                                                     String clientCertChainFilePath,
                                                     String clientPrivateKeyFilePath) throws SSLException {

        SslContextBuilder builder = GrpcSslContexts.forClient();
        if (trustCertCollectionFilePath != null) {
            builder.trustManager(new File(trustCertCollectionFilePath));
        }

        if (clientCertChainFilePath != null && clientPrivateKeyFilePath != null) {
            builder.keyManager(new File(clientCertChainFilePath), new File(clientPrivateKeyFilePath));
        }
        return builder.build();
    }

    // --------------- private methods of GrpcChannelsProvider ---------------

    /**
     * Returns a {@link io.grpc.Channel} for the specified channel configuration name.
     *
     * @param name the name of the channel configuration as specified in the configuration file
     * @return a new instance of {@link io.grpc.Channel}
     * @throws SSLException if any error during Channel creation
     * @throws NullPointerException if name is null
     * @throws IllegalArgumentException if name is empty
     */
    public Channel channel(String name)
            throws SSLException {
        if (name == null) {
            throw new NullPointerException("name cannot be null.");
        }
        if (name.trim().length() == 0) {
            throw new IllegalArgumentException("name cannot be empty or blank.");
        }
        GrpcChannelDescriptor chCfg = channelConfigs.get(name);
        if (chCfg == null) {
            throw new IllegalArgumentException("No channel configuration named " + name + " has been configured.");
        }

        return createChannel(chCfg);
    }

    /* package private */
    Map<String, GrpcChannelDescriptor> channels() {
        return channelConfigs;
    }

    private Channel createChannel(GrpcChannelDescriptor cfg)
            throws SSLException {

        ManagedChannel channel;
        if (cfg.sslDescriptor() == null || !cfg.sslDescriptor().isEnabled()) {
            ManagedChannelBuilder builder = ManagedChannelBuilder.forAddress(cfg.host(), cfg.port());
            channel = builder.usePlaintext().build();
        } else {
            channel = NettyChannelBuilder.forAddress(cfg.host(), cfg.port())
                    .negotiationType(NegotiationType.TLS)
                    .sslContext(createClientSslContext(cfg.sslDescriptor().tlsCaCert(),
                                                       cfg.sslDescriptor().tlsCert(),
                                                       cfg.sslDescriptor().tlsKey()))
                    .build();
        }
        return channel;
    }

    /**
     * Builder builds an instance of {@link GrpcChannelsProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<GrpcChannelsProvider> {

        private Map<String, GrpcChannelDescriptor> channelConfigs = new HashMap<>();

        private Builder(Config grpcConfigRoot) {

            // Add the default channel (which can be overridden in the config)
            channel("default", GrpcChannelDescriptor.builder().build());

            if (grpcConfigRoot == null) {
                return;
            }

            for (Config channelConfig : grpcConfigRoot.get("channels").asNodeList().get()) {
                String key = channelConfig.key().name();
                GrpcChannelDescriptor cfg = channelConfig.asNode().get().as(GrpcChannelDescriptor.class).get();

                channelConfigs.put(key, cfg);
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

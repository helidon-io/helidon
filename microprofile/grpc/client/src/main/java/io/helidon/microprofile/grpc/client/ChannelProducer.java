/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.config.Config;
import io.helidon.grpc.api.Grpc;

import io.grpc.Channel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

/**
 * A producer of gRPC {@link io.grpc.Channel Channels}.
 */
@ApplicationScoped
public class ChannelProducer {

    private final GrpcChannelsProvider provider;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Channel> channelMap = new HashMap<>();

    /**
     * Create a {@link ChannelProducer}.
     *
     * @param config the {@link io.helidon.config.Config} to use to configure
     *               the provided {@link io.grpc.Channel}s
     */
    @Inject
    ChannelProducer(Config config) {
        provider = GrpcChannelsProvider.create(config.get("grpc"));
    }

    /**
     * Produces a gRPC {@link io.grpc.Channel}.
     *
     * @param injectionPoint the injection point
     * @return a gRPC {@link io.grpc.Channel}
     */
    @Produces
    @Grpc.GrpcChannel(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME)
    public Channel get(InjectionPoint injectionPoint) {
        Grpc.GrpcChannel qualifier = injectionPoint.getQualifiers()
                .stream()
                .filter(q -> q.annotationType().equals(Grpc.GrpcChannel.class))
                .map(q -> (Grpc.GrpcChannel) q)
                .findFirst()
                .orElse(null);

        String name = (qualifier == null) ? GrpcChannelsProvider.DEFAULT_CHANNEL_NAME : qualifier.value();
        return findChannel(name);
    }

    /**
     * Produces the default gRPC {@link io.grpc.Channel}.
     *
     * @return the default gRPC {@link io.grpc.Channel}
     */
    @Produces
    public Channel getDefaultChannel() {
        return findChannel(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME);
    }

    /**
     * Obtain the named {@link io.grpc.Channel}.
     *
     * @param name the channel name
     * @return the named {@link io.grpc.Channel}
     */
    public Channel findChannel(String name) {
        try {
            lock.lock();
            return channelMap.computeIfAbsent(name, provider::channel);
        } finally {
            lock.unlock();
        }
    }
}

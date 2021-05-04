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

package io.helidon.microprofile.grpc.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import io.helidon.config.Config;
import io.helidon.grpc.client.GrpcChannelsProvider;
import io.helidon.microprofile.grpc.core.InProcessGrpcChannel;

import io.grpc.Channel;

/**
 * A producer of gRPC {@link io.grpc.Channel Channels}.
 */
@ApplicationScoped
public class ChannelProducer {

    private final GrpcChannelsProvider provider;

    private final Map<String, Channel> channelMap = new ConcurrentHashMap<>();

    /**
     * Create a {@link ChannelProducer}.
     *
     * @param config  the {@link io.helidon.config.Config} to use to configure
     *                the provided {@link io.grpc.Channel}s
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
    @GrpcChannel(name = GrpcChannelsProvider.DEFAULT_CHANNEL_NAME)
    public Channel get(InjectionPoint injectionPoint) {
        GrpcChannel qualifier = injectionPoint.getQualifiers()
                                        .stream()
                                        .filter(q -> q.annotationType().equals(GrpcChannel.class))
                                        .map(q -> (GrpcChannel) q)
                                        .findFirst()
                                        .orElse(null);

        String name = qualifier == null ? GrpcChannelsProvider.DEFAULT_CHANNEL_NAME : qualifier.name();

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
     * @return  the named {@link io.grpc.Channel}
     */
    Channel findChannel(String name) {
        return channelMap.computeIfAbsent(name, provider::channel);
    }

    /**
     * A utility method to obtain an in-process {@link io.grpc.Channel}.
     *
     * @param beanManager the CDI {@link BeanManager} to use to find the {@link io.grpc.Channel}
     * @return an in-process {@link io.grpc.Channel}
     */
    static Channel inProcessChannel(BeanManager beanManager) {
        return inProcessChannel(beanManager.createInstance());
    }

    /**
     * A utility method to obtain an in-process {@link io.grpc.Channel}.
     *
     * @param instance the CDI {@link Instance} to use to find the {@link io.grpc.Channel}
     * @return an in-process {@link io.grpc.Channel}
     */
    static Channel inProcessChannel(Instance<Object> instance) {
        return instance.select(Channel.class, InProcessGrpcChannel.Literal.INSTANCE).get();
    }
}

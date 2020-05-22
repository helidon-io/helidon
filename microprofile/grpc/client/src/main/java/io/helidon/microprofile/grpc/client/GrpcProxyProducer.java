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

package io.helidon.microprofile.grpc.client;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;

import io.helidon.grpc.client.GrpcChannelsProvider;
import io.helidon.microprofile.grpc.core.InProcessGrpcChannel;
import io.helidon.microprofile.grpc.core.ModelHelper;

import io.grpc.Channel;

/**
 * A utility class of gRPC CDI producer stubs.
 * <p>
 * The methods in this class are not real CDI producer methods,
 * they act as templates that the {@link GrpcClientCdiExtension}
 * will use to create producers on the fly as injection points
 * are observed.
 */
class GrpcProxyProducer {

    private GrpcProxyProducer() {
    }

    /**
     * A CDI producer method that produces a client proxy for a gRPC service that
     * will connect to the server using the channel specified via {@link GrpcChannel}
     * annotation on the proxy interface, or the default {@link Channel}.
     * <p>
     * This is not a real producer method but is used as a stub by the gRPC client
     * CDI extension to create real producers as injection points are discovered.
     *
     * @param injectionPoint the injection point where the client proxy is to be injected
     * @return a gRPC client proxy
     */
    @GrpcServiceProxy
    static Object proxyUsingDefaultChannel(InjectionPoint injectionPoint, ChannelProducer producer) {
        Class<?> type = ModelHelper.getGenericType(injectionPoint.getType());
        String channelName = type.isAnnotationPresent(GrpcChannel.class)
                ? type.getAnnotation(GrpcChannel.class).name()
                : GrpcChannelsProvider.DEFAULT_CHANNEL_NAME;
        Channel channel = producer.findChannel(channelName);
        GrpcClientProxyBuilder<?> builder = GrpcClientProxyBuilder.create(channel, type);
        return builder.build();
    }

    /**
     * A CDI producer method that produces a client proxy for a gRPC service that
     * will connect to the server using a named {@link Channel}.
     * <p>
     * This is not a real producer method but is used as a stub by the gRPC client
     * CDI extension to create real producers as injection points are discovered.
     *
     * @param injectionPoint the injection point where the client proxy is to be injected
     * @return a gRPC client proxy
     */
    @GrpcServiceProxy
    @GrpcChannel(name = GrpcChannelsProvider.DEFAULT_CHANNEL_NAME)
    static Object proxyUsingNamedChannel(InjectionPoint injectionPoint, ChannelProducer producer) {
        Class<?> type = ModelHelper.getGenericType(injectionPoint.getType());
        GrpcChannel channelName = injectionPoint.getAnnotated().getAnnotation(GrpcChannel.class);
        Channel channel = producer.findChannel(channelName.name());
        GrpcClientProxyBuilder<?> builder = GrpcClientProxyBuilder.create(channel, type);
        return builder.build();
    }

    /**
     * A CDI producer method that produces a client proxy for a gRPC service that
     * will connect to the server using an in-process {@link Channel}.
     * <p>
     * This is not a real producer method but is used as a stub by the gRPC client
     * CDI extension to create real producers as injection points are discovered.
     *
     * @param injectionPoint the injection point where the client proxy is to be injected
     * @return a gRPC client proxy
     */
    @GrpcServiceProxy
    @InProcessGrpcChannel
    static Object proxyUsingInProcessChannel(InjectionPoint injectionPoint, BeanManager beanManager) {
        Class<?> type = ModelHelper.getGenericType(injectionPoint.getType());
        Channel channel = ChannelProducer.inProcessChannel(beanManager);
        GrpcClientProxyBuilder<?> builder = GrpcClientProxyBuilder.create(channel, type);
        return builder.build();
    }
}

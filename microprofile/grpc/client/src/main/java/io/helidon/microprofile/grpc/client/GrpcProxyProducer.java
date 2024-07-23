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

import io.helidon.grpc.api.Grpc;
import io.helidon.microprofile.grpc.core.ModelHelper;

import io.grpc.Channel;
import jakarta.enterprise.inject.spi.InjectionPoint;

/**
 * A utility class of gRPC CDI producer stubs.
 * <p>
 * The methods in this class are not real CDI producer methods,
 * they act as templates that the {@link io.helidon.microprofile.grpc.client.GrpcClientCdiExtension}
 * will use to create producers on the fly as injection points
 * are observed.
 */
class GrpcProxyProducer {

    private GrpcProxyProducer() {
    }

    /**
     * A CDI producer method that produces a client proxy for a gRPC service that
     * will connect to the server using the channel specified via
     * {@link io.helidon.grpc.api.Grpc.GrpcChannel} annotation on the proxy interface
     * or injection point, or the default {@link io.grpc.Channel}.
     * <p>
     * This is not a real producer method but is used as a stub by the gRPC client
     * CDI extension to create real producers as injection points are discovered.
     *
     * @param injectionPoint the injection point where the client proxy is to be injected
     * @return a gRPC client proxy
     */
    @Grpc.GrpcProxy
    @Grpc.GrpcChannel(value = GrpcChannelsProvider.DEFAULT_CHANNEL_NAME)
    static Object proxyUsingNamedChannel(InjectionPoint injectionPoint, ChannelProducer producer) {
        Class<?> type = ModelHelper.getGenericType(injectionPoint.getType());

        String channelName;
        if (injectionPoint.getAnnotated().isAnnotationPresent(Grpc.GrpcChannel.class)) {
            channelName = injectionPoint.getAnnotated().getAnnotation(Grpc.GrpcChannel.class).value();
        } else {
            channelName = type.isAnnotationPresent(Grpc.GrpcChannel.class)
                    ? type.getAnnotation(Grpc.GrpcChannel.class).value()
                    : GrpcChannelsProvider.DEFAULT_CHANNEL_NAME;
        }

        Channel channel = producer.findChannel(channelName);
        GrpcProxyBuilder<?> builder = GrpcProxyBuilder.create(channel, type);

        return builder.build();
    }
}

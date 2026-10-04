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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.grpc.core.WeightedBag;
import io.helidon.webclient.api.HttpTransportConnectionCache;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.spi.GrpcClientService;
import io.helidon.webclient.http2.Http2Client;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;

class GrpcClientImpl implements GrpcClient {

    private static final ClientInterceptor[] NO_INTERCEPTORS = new ClientInterceptor[]{};

    private final WebClient webClient;
    private final Http2Client http2Client;
    private final GrpcClientConfig clientConfig;
    private final HttpTransportConnectionCache<GrpcClientConnections> observedConnections;

    GrpcClientImpl(WebClient webClient, GrpcClientConfig clientConfig) {
        this.webClient = webClient;
        this.http2Client = webClient.client(Http2Client.PROTOCOL);
        this.clientConfig = clientConfig;
        this.observedConnections = HttpTransportConnectionCache.create(GrpcClientConnections.class,
                                                                       clientConfig,
                                                                       GrpcClientConnections::new)
                .orElse(null);
    }

    WebClient webClient() {
        return webClient;
    }

    Http2Client http2Client() {
        return http2Client;
    }

    GrpcClientConnections.Registration connectionRegistration() {
        if (observedConnections == null) {
            return null;
        }
        return observedConnections.cache().registration(observedConnections.observer());
    }

    @Override
    public void closeResource() {
        if (observedConnections != null) {
            observedConnections.closeResource();
        }
    }

    @Override
    public CompletionStage<Void> closeResourceAsync() {
        closeResource();
        return observedConnections == null ? CompletableFuture.completedFuture(null) : observedConnections.completion();
    }

    @Override
    public GrpcClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public GrpcServiceClient serviceClient(GrpcServiceDescriptor descriptor) {
        return new GrpcServiceClientImpl(descriptor, this);
    }

    @Override
    public Channel channel() {
        return channel(NO_INTERCEPTORS);
    }

    @Override
    public Channel channel(ClientInterceptor... interceptors) {
        Channel channel = new GrpcChannel(this);
        WeightedBag<ClientInterceptor> weightedBag = allInterceptors(interceptors);
        if (!weightedBag.isEmpty()) {
            List<ClientInterceptor> orderedInterceptors = weightedBag.stream().toList().reversed();
            channel = ClientInterceptors.intercept(channel, orderedInterceptors);
        }
        return channel;

    }

    @Override
    public GrpcClientConfig clientConfig() {
        return clientConfig;
    }

    private WeightedBag<ClientInterceptor> allInterceptors(ClientInterceptor... interceptors) {
        WeightedBag<ClientInterceptor> weightedBag = WeightedBag.create();
        for (ClientInterceptor interceptor : interceptors) {
            weightedBag.add(interceptor);
        }
        List<GrpcClientService> grpcServices = clientConfig.grpcServices();
        for (GrpcClientService service : grpcServices) {
            weightedBag.merge(service.interceptors());
        }
        return weightedBag;
    }
}

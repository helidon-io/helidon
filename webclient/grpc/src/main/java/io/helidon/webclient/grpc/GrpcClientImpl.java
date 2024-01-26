/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2Client;

class GrpcClientImpl implements GrpcClient {
    private final WebClient webClient;
    private final Http2Client http2Client;
    private final GrpcClientConfig clientConfig;

    GrpcClientImpl(WebClient webClient, GrpcClientConfig clientConfig) {
        this.webClient = webClient;
        this.http2Client = webClient.client(Http2Client.PROTOCOL);
        this.clientConfig = clientConfig;
    }

    public WebClient webClient() {
        return webClient;
    }

    public Http2Client http2Client() {
        return http2Client;
    }

    @Override
    public GrpcClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public GrpcServiceClient serviceClient(GrpcServiceDescriptor descriptor) {
        return new GrpcServiceClientImpl(descriptor, this);
    }
}

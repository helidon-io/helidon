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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * A dynamic proxy that forwards methods to gRPC call handlers.
 */
class ClientProxy
        implements InvocationHandler {

    private final GrpcServiceClient client;

    /**
     * A map of Java method name to gRPR method name.
     */
    private final Map<String, String> names;

    /**
     * Create a {@link ClientProxy}.
     *
     * @param client  the {@link io.helidon.grpc.client.GrpcServiceClient} to use
     * @param names   a map of Java method names to gRPC method names
     */
    private ClientProxy(GrpcServiceClient client, Map<String, String> names) {
        this.client = client;
        this.names = names;
    }

    /**
     * Create a {@link ClientProxy} instance.
     *
     * @param client  the {@link io.helidon.grpc.client.GrpcServiceClient} to use
     * @param names   a map of Java method names to gRPC method names
     * @return a {@link ClientProxy} instance for the specified service client
     */
    static ClientProxy create(GrpcServiceClient client, Map<String, String> names) {
        return new ClientProxy(client, names);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return client.invoke(names.get(method.getName()), args);
    }

    /**
     * Obtain the underlying {@link GrpcServiceClient}.
     * @return  the underlying {@link GrpcServiceClient}
     */
    public GrpcServiceClient getClient() {
        return client;
    }
}

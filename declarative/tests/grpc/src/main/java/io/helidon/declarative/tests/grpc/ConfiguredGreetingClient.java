/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.grpc;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingReply;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.grpc.api.Grpc;
import io.helidon.webclient.grpc.RpcClient;

@RpcClient.Endpoint(value = ClientConfigGreetingClients.SERVER_URI,
                    configKey = ClientConfigGreetingClients.CONFIG_KEY,
                    clientName = ClientConfigGreetingClients.BROKEN_CLIENT)
@Grpc.GrpcService(ClientConfigGreetingClients.SERVICE_NAME)
@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)
interface ConfiguredGreetingClient {
    @Grpc.Unary("Greet")
    GreetingReply greet(GreetingRequest request);
}

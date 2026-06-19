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

import java.util.Iterator;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingReply;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.grpc.api.Grpc;
import io.helidon.webclient.grpc.GrpcClient;

/**
 * Typed declarative gRPC client for the greeting service.
 */
@GrpcClient.Endpoint(value = ClientConfigGreetingClients.SERVER_URI,
                     clientName = ClientConfigGreetingClients.MISSING_CLIENT)
@Grpc.GrpcService(ClientConfigGreetingClients.SERVICE_NAME)
public interface GreetingClient {
    /**
     * Invoke the unary greeting RPC.
     *
     * @param request greeting request
     * @return greeting reply
     */
    @Grpc.Unary("Greet")
    GreetingReply greet(GreetingRequest request);

    /**
     * Invoke the server-streaming split RPC.
     *
     * @param request greeting request
     * @return streamed greeting replies
     */
    @Grpc.ServerStreaming("Split")
    Iterator<GreetingReply> split(GreetingRequest request);

    /**
     * Invoke the client-streaming join RPC.
     *
     * @param requests streamed greeting requests
     * @return joined greeting reply
     */
    @Grpc.ClientStreaming("Join")
    GreetingReply join(Iterator<GreetingRequest> requests);

    /**
     * Invoke the bidirectional chat RPC.
     *
     * @param requests streamed greeting requests
     * @return streamed greeting replies
     */
    @Grpc.Bidirectional("Chat")
    Iterator<GreetingReply> chat(Iterator<GreetingRequest> requests);
}

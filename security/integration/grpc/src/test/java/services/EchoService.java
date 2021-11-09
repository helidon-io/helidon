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

package services;

import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.grpc.server.test.Echo;

import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A simple test gRPC echo service.
 */
public class EchoService
        implements GrpcService {

    @Override
    public void update(ServiceDescriptor.Rules rules) {
        rules.proto(Echo.getDescriptor())
                .unary("Echo", this::echo);
    }

    /**
     * Echo the message back to the caller.
     *
     * @param request   the echo request containing the message to echo
     * @param observer  the call response
     */
    public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
        String message = request.getMessage();
        Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(message).build();
        complete(observer, response);
    }
}

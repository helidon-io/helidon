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

package services;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.grpc.server.test.Echo.EchoRequest;
import io.helidon.grpc.server.test.Echo.EchoResponse;
import io.helidon.microprofile.grpc.core.RpcService;
import io.helidon.microprofile.grpc.core.Unary;

import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A simple test gRPC echo service.
 */
@RpcService
@ApplicationScoped
public class EchoService {

    /**
     * Echo the message back to the caller.
     *
     * @param request   the echo request containing the message to echo
     * @param observer  the call response
     */
    @Unary
    @Timed
    public void echo(EchoRequest request, StreamObserver<EchoResponse> observer) {
        String message = request.getMessage();
        EchoResponse response = EchoResponse.newBuilder().setMessage(message).build();
        complete(observer, response);
    }
}

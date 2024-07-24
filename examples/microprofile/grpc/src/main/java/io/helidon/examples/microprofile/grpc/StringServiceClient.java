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

package io.helidon.examples.microprofile.grpc;

import io.helidon.grpc.api.Grpc;

import io.grpc.stub.StreamObserver;

@Grpc.GrpcService("StringService")
@Grpc.GrpcChannel("string-channel")         // see application.yaml
public interface StringServiceClient {

    @Grpc.Unary("Upper")
    Strings.StringMessage upper(Strings.StringMessage request);

    @Grpc.Unary("Lower")
    Strings.StringMessage lower(Strings.StringMessage request);

    @Grpc.ServerStreaming("Split")
    void split(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer);

    @Grpc.ClientStreaming("Join")
    StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> observer);
}


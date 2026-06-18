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

import java.util.StringJoiner;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingReply;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.grpc.api.Grpc;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;
import io.helidon.service.registry.Service;

import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.security.RolesAllowed;

@Grpc.GrpcService("GreetingService")
@Service.Singleton
class GreetingEndpoint {
    @Grpc.Proto
    static Descriptors.FileDescriptor proto() {
        return DeclarativeGrpcProto.getDescriptor();
    }

    @Grpc.Unary("Greet")
    GreetingReply greet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("SecureGreet")
    @Authenticated
    GreetingReply secureGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("AuthorizedGreet")
    @Authenticated
    @Authorized
    GreetingReply authorizedGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("AdminGreet")
    @RolesAllowed("admin")
    GreetingReply adminGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.ServerStreaming("Split")
    void split(GreetingRequest request, StreamObserver<GreetingReply> responseObserver) {
        for (String name : request.getName().split(",")) {
            responseObserver.onNext(reply(name.strip()));
        }
        responseObserver.onCompleted();
    }

    @Grpc.ClientStreaming("Join")
    StreamObserver<GreetingRequest> join(StreamObserver<GreetingReply> responseObserver) {
        StringJoiner names = new StringJoiner(", ");
        return new StreamObserver<>() {
            @Override
            public void onNext(GreetingRequest request) {
                names.add(request.getName().strip());
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(reply(names.toString()));
                responseObserver.onCompleted();
            }
        };
    }

    @Grpc.Bidirectional("Chat")
    StreamObserver<GreetingRequest> chat(StreamObserver<GreetingReply> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(GreetingRequest request) {
                responseObserver.onNext(reply(request.getName()));
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private GreetingReply reply(String name) {
        return GreetingReply.newBuilder()
                .setMessage("Hello " + name)
                .build();
    }
}

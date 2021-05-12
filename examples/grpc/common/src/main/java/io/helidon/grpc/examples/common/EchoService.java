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

package io.helidon.grpc.examples.common;


import io.grpc.stub.StreamObserver;

/**
 * An implementation of the protocol buffer generated EchoService.
 */
public class EchoService
        extends EchoServiceGrpc.EchoServiceImplBase {

    @Override
    public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
        observer.onNext(Echo.EchoResponse.newBuilder().setMessage(request.getMessage()).build());
        observer.onCompleted();
    }
}

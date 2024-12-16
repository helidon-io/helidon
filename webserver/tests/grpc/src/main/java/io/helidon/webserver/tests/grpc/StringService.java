/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.grpc;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.grpc.core.CollectingObserver;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.grpc.strings.Strings.StringMessage;

import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static io.helidon.grpc.core.ResponseHelper.stream;

class StringService implements GrpcService {
    @Override
    public Descriptors.FileDescriptor proto() {
        return Strings.getDescriptor();
    }

    @Override
    public void update(Routing router) {
        router.unary("Upper", this::grpcUnaryUpper)
                .unary("Lower", this::grpcUnaryLower)
                .bidi("Echo", this::grpcBidi)
                .serverStream("Split", this::grpcServerStream)
                .clientStream("Join", this::grpcClientStream);
    }

    private void grpcServerStream(StringMessage request, StreamObserver<StringMessage> observer) {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
    }

    private StreamObserver<StringMessage> grpcClientStream(StreamObserver<StringMessage> observer) {
        return CollectingObserver.create(
                Collectors.joining(" "),
                observer,
                StringMessage::getText,
                this::response);
    }

    private StreamObserver<StringMessage> grpcBidi(StreamObserver<StringMessage> observer) {
        return new StreamObserver<>() {
            public void onNext(StringMessage value) {
                observer.onNext(value);
            }

            public void onError(Throwable t) {
                t.printStackTrace();
            }

            public void onCompleted() {
                observer.onCompleted();
            }
        };
    }

    private void grpcUnaryUpper(StringMessage request, StreamObserver<StringMessage> observer) {
        String requestText = request.getText();
        complete(observer, StringMessage.newBuilder()
                .setText(requestText.toUpperCase(Locale.ROOT))
                .build());
    }

    private void grpcUnaryLower(StringMessage request, StreamObserver<StringMessage> observer) {
        String requestText = request.getText();
        complete(observer, StringMessage.newBuilder()
                .setText(requestText.toLowerCase(Locale.ROOT))
                .build());
    }

    private StringMessage response(String text) {
        return StringMessage.newBuilder().setText(text).build();
    }
}

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

import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.grpc.examples.common.Strings.StringMessage;
import io.helidon.grpc.server.CollectingObserver;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;

import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static io.helidon.grpc.core.ResponseHelper.stream;

/**
 * AN implementation of the StringService.
 */
public class StringService
        implements GrpcService {
    @Override
    public void update(ServiceDescriptor.Rules rules) {
        rules.proto(Strings.getDescriptor())
                .unary("Upper", this::upper)
                .unary("Lower", this::lower)
                .serverStreaming("Split", this::split)
                .clientStreaming("Join", this::join)
                .bidirectional("Echo", this::echo);
    }

    // ---- service methods -------------------------------------------------

    private void upper(StringMessage request, StreamObserver<StringMessage> observer) {
        complete(observer, response(request.getText().toUpperCase()));
    }

    private void lower(StringMessage request, StreamObserver<StringMessage> observer) {
        complete(observer, response(request.getText().toLowerCase()));
    }

    private void split(StringMessage request, StreamObserver<StringMessage> observer) {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
    }

    private StreamObserver<StringMessage> join(StreamObserver<StringMessage> observer) {
        return new CollectingObserver<>(
                Collectors.joining(" "),
                observer,
                StringMessage::getText,
                this::response);
    }

    private StreamObserver<StringMessage> echo(StreamObserver<StringMessage> observer) {
        return new StreamObserver<StringMessage>() {
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

    // ---- helper methods --------------------------------------------------

    private StringMessage response(String text) {
        return StringMessage.newBuilder().setText(text).build();
    }

}

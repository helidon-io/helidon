/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import io.grpc.stub.StreamObserver;
import io.helidon.grpc.core.CollectingObserver;
import io.helidon.webserver.grpc.strings.StringServiceGrpc;
import io.helidon.webserver.grpc.strings.Strings;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static io.helidon.grpc.core.ResponseHelper.stream;

public class BindableStringService extends StringServiceGrpc.StringServiceImplBase {

    @Override
    public void upper(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String requestText = request.getText();
        complete(observer, Strings.StringMessage.newBuilder()
                .setText(requestText.toUpperCase(Locale.ROOT))
                .build());
    }

    @Override
    public void lower(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String requestText = request.getText();
        complete(observer, Strings.StringMessage.newBuilder()
                .setText(requestText.toLowerCase(Locale.ROOT))
                .build());
    }

    @Override
    public void split(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
    }

    @Override
    public StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> observer) {
        return new CollectingObserver<>(
                Collectors.joining(" "),
                observer,
                Strings.StringMessage::getText,
                this::response);
    }

    @Override
    public StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> observer) {
        return new StreamObserver<>() {
            public void onNext(Strings.StringMessage value) {
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

    private Strings.StringMessage response(String text) {
        return Strings.StringMessage.newBuilder().setText(text).build();
    }
}

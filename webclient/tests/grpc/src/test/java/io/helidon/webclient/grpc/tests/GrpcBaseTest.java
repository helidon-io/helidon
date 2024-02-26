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
package io.helidon.webclient.grpc.tests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import io.grpc.stub.StreamObserver;

class GrpcBaseTest {

    static void upper(Strings.StringMessage req,
                              StreamObserver<Strings.StringMessage> streamObserver) {
        Strings.StringMessage msg = Strings.StringMessage.newBuilder()
                .setText(req.getText().toUpperCase(Locale.ROOT))
                .build();
        streamObserver.onNext(msg);
        streamObserver.onCompleted();
    }

    static void split(Strings.StringMessage req,
                              StreamObserver<Strings.StringMessage> streamObserver) {
        String[] strings = req.getText().split(" ");
        for (String string : strings) {
            streamObserver.onNext(Strings.StringMessage.newBuilder()
                    .setText(string)
                    .build());

        }
        streamObserver.onCompleted();
    }

    static StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> streamObserver) {
        return new StreamObserver<>() {
            private StringBuilder builder;

            @Override
            public void onNext(Strings.StringMessage value) {
                if (builder == null) {
                    builder = new StringBuilder();
                    builder.append(value.getText());
                } else {
                    builder.append(" ").append(value.getText());
                }
            }

            @Override
            public void onError(Throwable t) {
                streamObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                streamObserver.onNext(Strings.StringMessage.newBuilder()
                        .setText(builder.toString())
                        .build());
                streamObserver.onCompleted();
            }
        };
    }

    static StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> streamObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(Strings.StringMessage value) {
                streamObserver.onNext(value);
            }

            @Override
            public void onError(Throwable t) {
                streamObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                streamObserver.onCompleted();
            }
        };
    }

    Strings.StringMessage newStringMessage(String data) {
        return Strings.StringMessage.newBuilder().setText(data).build();
    }

    static <ReqT> StreamObserver<ReqT> singleStreamObserver(CompletableFuture<ReqT> future) {
        return new StreamObserver<>() {
            private ReqT value;

            @Override
            public void onNext(ReqT value) {
                this.value = value;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(value);
            }
        };
    }

    static <ResT> StreamObserver<ResT> multiStreamObserver(CompletableFuture<Iterator<ResT>> future) {
        return new StreamObserver<>() {
            private final List<ResT> value = new ArrayList<>();

            @Override
            public void onNext(ResT value) {
                this.value.add(value);
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(value.iterator());
            }
        };
    }
}

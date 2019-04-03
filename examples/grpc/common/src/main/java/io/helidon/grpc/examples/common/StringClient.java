/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.grpc.examples.common.Strings.StringMessage;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * A client to the {@link io.helidon.grpc.examples.common.StringService}.
 *
 * @author Aleksandar Seovic
 */
public class StringClient {

    private StringClient() {
    }

    /**
     * Program entry point.
     *
     * @param args  the program arguments
     *
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408).usePlaintext().build();

        StringServiceGrpc.StringServiceStub stub = StringServiceGrpc.newStub(channel);
        stub.lower(stringMessage("Convert To Lowercase"), new PrintObserver<>());
        Thread.sleep(500L);
        stub.upper(stringMessage("Convert to Uppercase"), new PrintObserver<>());
        Thread.sleep(500L);
        stub.split(stringMessage("Let's split some text"), new PrintObserver<>());
        Thread.sleep(500L);

        StreamObserver<StringMessage> sender = stub.join(new PrintObserver<>());
        sender.onNext(stringMessage("Let's"));
        sender.onNext(stringMessage("join"));
        sender.onNext(stringMessage("some"));
        sender.onNext(stringMessage("text"));
        sender.onCompleted();
        Thread.sleep(500L);

        sender = stub.echo(new PrintObserver<>());
        sender.onNext(stringMessage("Let's"));
        sender.onNext(stringMessage("echo"));
        sender.onNext(stringMessage("some"));
        sender.onNext(stringMessage("text"));
        sender.onCompleted();
        Thread.sleep(500L);
    }

    private static StringMessage stringMessage(String text) {
        return StringMessage.newBuilder().setText(text).build();
    }

    static class PrintObserver<T> implements StreamObserver<T> {
        public void onNext(T value) {
            System.out.println(value);
        }

        public void onError(Throwable t) {
            t.printStackTrace();
        }

        public void onCompleted() {
            System.out.println("<completed>");
        }
    }
}

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

import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.grpc.examples.common.Strings.StringMessage;
import io.helidon.grpc.server.CollectingObserver;
import io.helidon.microprofile.grpc.core.Bidirectional;
import io.helidon.microprofile.grpc.core.ClientStreaming;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.ServerStreaming;
import io.helidon.microprofile.grpc.core.Unary;

import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static io.helidon.grpc.core.ResponseHelper.stream;

/**
 * An implementation of the StringService with metrics annotated methods.
 */
@Grpc
@ApplicationScoped
public class StringService {

    @Counted
    @Unary(name = "Upper")
    public void upper(StringMessage request, StreamObserver<StringMessage> observer) {
        complete(observer, response(request.getText().toUpperCase()));
    }

    @Metered
    @Unary(name = "Lower")
    public void lower(StringMessage request, StreamObserver<StringMessage> observer) {
        complete(observer, response(request.getText().toLowerCase()));
    }

    @Timed
    @ServerStreaming(name = "Split")
    public void split(StringMessage request, StreamObserver<StringMessage> observer) {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
    }

    @ClientStreaming(name = "Join")
    public StreamObserver<StringMessage> join(StreamObserver<StringMessage> observer) {
        return new CollectingObserver<>(
                Collectors.joining(" "),
                observer,
                StringMessage::getText,
                this::response);
    }

    @Bidirectional(name = "Echo")
    public StreamObserver<StringMessage> echo(StreamObserver<StringMessage> observer) {
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

    private StringMessage response(String text) {
        return StringMessage.newBuilder().setText(text).build();
    }
}

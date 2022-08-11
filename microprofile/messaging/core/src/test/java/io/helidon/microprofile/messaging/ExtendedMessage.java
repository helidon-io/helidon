/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.microprofile.reactive.messaging.Message;

public class ExtendedMessage<T> implements Message<T> {

    private final Supplier<CompletionStage<Void>> ack;
    private final T payload;

    private ExtendedMessage(T payload, Supplier<CompletionStage<Void>> ack) {
        this.payload = payload;
        this.ack = ack;
    }

    public static <P> ExtendedMessage<P> of(P payload) {
        return new ExtendedMessage<>(payload, () -> CompletableFuture.completedStage(null));
    }

    public static <P> ExtendedMessage<P> of(P payload, Supplier<CompletionStage<Void>> ack) {
        return new ExtendedMessage<>(payload, ack);
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public CompletionStage<Void> ack() {
        return ack.get();
    }
}

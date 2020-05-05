/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.SubmissionPublisher;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public final class Emitter<PAYLOAD> implements Publisher<Message<PAYLOAD>> {

    private SubmissionPublisher<Message<PAYLOAD>> submissionPublisher;
    private final Set<Channel<PAYLOAD>> channels = new HashSet<>();

    private Emitter() {
    }

    void init(Executor executor, int maxBufferCapacity) {
        submissionPublisher = new SubmissionPublisher<>(executor, maxBufferCapacity);
    }

    public CompletionStage<Void> send(PAYLOAD msg) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        submissionPublisher.submit(Message.of(msg, () -> {
            future.complete(null);
            return CompletableFuture.completedStage(null);
        }));
        return future;
    }

    public void send(Message<PAYLOAD> msg) {
        submissionPublisher.submit(msg);
    }

    public void complete() {
        submissionPublisher.close();
    }

    public void error(Exception e) {
        submissionPublisher.closeExceptionally(e);
    }

    @Override
    public void subscribe(final Subscriber<? super Message<PAYLOAD>> s) {
        submissionPublisher.subscribe(FlowAdapters.toFlowSubscriber(ContextSubscriber.create("emitter-message", s)));
    }

    Set<Channel<PAYLOAD>> channels() {
        return this.channels;
    }

    public static <PAYLOAD> Emitter<PAYLOAD> create(Channel<PAYLOAD> channel) {
        Emitter.Builder<PAYLOAD> builder = Emitter.<PAYLOAD>builder()
                .channel(channel);
        return builder.build();
    }

    public static <PAYLOAD> Emitter<PAYLOAD> create(Channel<PAYLOAD> channel, Channel<PAYLOAD>... channels) {
        Emitter.Builder<PAYLOAD> builder = Emitter.<PAYLOAD>builder()
                .channel(channel);
        for (Channel<PAYLOAD> ch : channels) {
            builder.channel(ch);
        }
        return builder.build();
    }

    public static <PAYLOAD> Emitter.Builder<PAYLOAD> builder() {
        return new Emitter.Builder<PAYLOAD>();
    }

    public static <PAYLOAD> Emitter.Builder<PAYLOAD> builder(Class<PAYLOAD> clazz) {
        return new Emitter.Builder<PAYLOAD>();
    }

    public final static class Builder<PAYLOAD> implements io.helidon.common.Builder<Emitter<PAYLOAD>> {

        private final Emitter<PAYLOAD> emitter = new Emitter<>();

        public Builder<PAYLOAD> channel(Channel<PAYLOAD> channel) {
            emitter.channels.add(channel);
            return this;
        }

        @Override
        public Emitter<PAYLOAD> build() {
            return emitter;
        }

        @Override
        public Emitter<PAYLOAD> get() {
            return emitter;
        }
    }
}

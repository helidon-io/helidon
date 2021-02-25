/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

/**
 * Emitter is convenience publisher for one or multiple channels,
 * publishing is as easy as calling {@link Emitter#send(Object)} method.
 *
 * <pre>{@code
 *  Channel<String> simpleChannel = Channel.create();
 *
 *  Emitter<String> emitter = Emitter.create(simpleChannel);
 *
 *  Messaging messaging = Messaging.builder()
 *          .emitter(emitter)
 *          .listener(simpleChannel, System.out::println)
 *          .build();
 *
 *  messaging.start();
 *
 *  emitter.send(Message.of("Hello world!"));
 * }</pre>
 *
 * @param <PAYLOAD> message payload type
 */
public final class Emitter<PAYLOAD> implements Publisher<Message<PAYLOAD>> {

    static final String EMITTER_CONTEXT_PREFIX = "emitter-message";
    private SubmissionPublisher<Message<PAYLOAD>> submissionPublisher;
    private final Set<Channel<PAYLOAD>> channels = new HashSet<>();

    private Emitter() {
    }

    void init(Executor executor, int maxBufferCapacity) {
        submissionPublisher = new SubmissionPublisher<>(executor, maxBufferCapacity);
    }

    /**
     * Send raw payload to downstream, wrapped to {@link Message} when demand is higher than 0.
     * Publishes the given item to each current subscriber by asynchronously invoking
     * its onNext method, blocking uninterruptibly while resources for any subscriber
     * are unavailable.
     *
     * @param msg payload to be wrapped and sent(or buffered if there is no demand)
     * @return callback being invoked when message is acked
     */
    public CompletionStage<Void> send(PAYLOAD msg) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        submissionPublisher.submit(Message.of(msg, () -> {
            future.complete(null);
            return CompletableFuture.completedStage(null);
        }));
        return future;
    }

    /**
     * Send raw payload to downstream, wrapped to {@link Message} when demand is higher than 0.
     * Publishes the given item to each current subscriber by asynchronously invoking
     * its onNext method, blocking uninterruptibly while resources for any subscriber
     * are unavailable.
     *
     * @param msg payload to be wrapped and sent(or buffered if there is no demand)
     */
    public void sendBlocking(PAYLOAD msg) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        submissionPublisher.submit(Message.of(msg, () -> {
            future.complete(null);
            return null;
        }));
        future.join();
    }

    /**
     * Send {@link Message} to downstream when demand is higher than 0. Publishes the given item
     * to each current subscriber by asynchronously invoking its onNext method,
     * blocking uninterruptibly while resources for any subscriber are unavailable.
     *
     * @param msg message wrapper with payload
     * @return estimate of the maximum lag (number of items submitted but not yet consumed)
     * among all current subscribers. This value is at least one (accounting for this
     * submitted item) if there are any subscribers, else zero.
     * @throws IllegalStateException                           if emitter has been already completed
     * @throws NullPointerException                            if message is null
     * @throws java.util.concurrent.RejectedExecutionException if thrown by Executor
     */
    public int send(Message<PAYLOAD> msg) {
        return submissionPublisher.submit(msg);
    }

    /**
     * Send onComplete signal to all subscribers.
     */
    public void complete() {
        submissionPublisher.close();
    }

    /**
     * Send onError signal to all subscribers.
     *
     * @param e error to send in onError signal downstream
     */
    public void error(Exception e) {
        submissionPublisher.closeExceptionally(e);
    }

    @Override
    public void subscribe(final Subscriber<? super Message<PAYLOAD>> s) {
        submissionPublisher.subscribe(FlowAdapters.toFlowSubscriber(ContextSubscriber.create(EMITTER_CONTEXT_PREFIX, s)));
    }

    Set<Channel<PAYLOAD>> channels() {
        return this.channels;
    }

    /**
     * Create new Emitter to serve as a publisher for supplied channel.
     *
     * @param channel   to serve as publisher in
     * @param <PAYLOAD> message payload type
     * @return new emitter
     */
    public static <PAYLOAD> Emitter<PAYLOAD> create(Channel<PAYLOAD> channel) {
        Emitter.Builder<PAYLOAD> builder = Emitter.<PAYLOAD>builder()
                .channel(channel);
        return builder.build();
    }

    /**
     * Create new Emitter to serve as a broadcast publisher for supplied channels.
     *
     * @param channel   to serve as publisher in
     * @param channels  to serve as publisher for
     * @param <PAYLOAD> message payload type
     * @return new emitter
     */
    public static <PAYLOAD> Emitter<PAYLOAD> create(Channel<PAYLOAD> channel, Channel<PAYLOAD>... channels) {
        Emitter.Builder<PAYLOAD> builder = Emitter.<PAYLOAD>builder()
                .channel(channel);
        for (Channel<PAYLOAD> ch : channels) {
            builder.channel(ch);
        }
        return builder.build();
    }

    /**
     * Prepare new builder for Emitter construction.
     *
     * @param <PAYLOAD> message payload type
     * @return new emitter builder
     */
    public static <PAYLOAD> Emitter.Builder<PAYLOAD> builder() {
        return new Emitter.Builder<PAYLOAD>();
    }

    /**
     * Builder for {@link io.helidon.messaging.Emitter}.
     *
     * @param <PAYLOAD> message payload type
     */
    public static final class Builder<PAYLOAD> implements io.helidon.common.Builder<Emitter<PAYLOAD>> {

        private final Emitter<PAYLOAD> emitter = new Emitter<>();

        /**
         * Add new {@link io.helidon.messaging.Channel} for Emitter to publish to.
         *
         * @param channel to serve as publisher in
         * @return this builder
         */
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

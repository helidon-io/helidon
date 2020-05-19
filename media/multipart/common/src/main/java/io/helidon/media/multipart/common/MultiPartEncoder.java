/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart.common;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * Reactive processor that encodes a stream of {@link BodyPart} into an HTTP
 * payload. This processor is a single use publisher that supports a single
 * subscriber, it is not resumable.
 */
public class MultiPartEncoder implements Processor<WriteableBodyPart, DataChunk> {

    private final MessageBodyWriterContext context;
    private final String boundary;
    private Subscriber<? super DataChunk> subscriber;
    private Subscription subscription;
    private final CompletableFuture<EmittingPublisher<Flow.Publisher<DataChunk>>> emitterFuture = new CompletableFuture<>();
    private EmittingPublisher<Flow.Publisher<DataChunk>> emitter;

    public MultiPartEncoder(String boundary, MessageBodyWriterContext context) {
        Objects.requireNonNull(boundary, "boundary cannot be null!");
        Objects.requireNonNull(context, "context cannot be null!");
        this.context = context;
        this.boundary = boundary;
    }

    public static MultiPartEncoder create(String boundary, MessageBodyWriterContext context) {
        return new MultiPartEncoder(boundary, context);
    }

    @Override
    public void subscribe(final Subscriber<? super DataChunk> subscriber) {
        Objects.requireNonNull(subscriber);
        if(this.subscriber != null){
            subscriber.onSubscribe(SubscriptionHelper.CANCELED);
            subscriber.onError(new IllegalStateException("Only one Subscriber allowed"));
            return;
        }
        this.subscriber = subscriber;
        deferredInit();
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        SubscriptionHelper.validate(this.subscription, subscription);
        this.subscription = subscription;
        deferredInit();
    }

    private void deferredInit() {
        if (subscription != null && subscriber != null) {
            emitter = EmittingPublisher.create();
            // relay request to upstream, already reduced by flatmap
            emitter.onRequest(subscription::request);
            Multi.from(emitter)
                    .flatMap(Function.identity())
                    .subscribe(subscriber);
            emitterFuture.complete(emitter);
        }
    }

    @Override
    public void onNext(final WriteableBodyPart bodyPart) {
        emitter.emit(createBodyPartPublisher(bodyPart));
    }

    private Flow.Publisher<DataChunk> createBodyPartPublisher(final WriteableBodyPart bodyPart) {
        Map<String, List<String>> headers = bodyPart.headers().toMap();
        StringBuilder sb = new StringBuilder();

        // start boundary
        sb.append("--").append(boundary).append("\r\n");

        // headers lines
        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {

            String headerName = headerEntry.getKey();
            for (String headerValue : headerEntry.getValue()) {
                sb.append(headerName)
                        .append(":")
                        .append(headerValue)
                        .append("\r\n");
            }
        }

        // end of headers empty line
        sb.append("\r\n");
        return Multi.concat(Multi.concat(
                // Part prefix
                Single.just(DataChunk.create(sb.toString().getBytes(StandardCharsets.UTF_8))),
                // Part body
                bodyPart.content().toPublisher(context)),
                // Part postfix
                Single.just(DataChunk.create("\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public void onError(final Throwable throwable) {
        Objects.requireNonNull(throwable);
        emitterFuture.whenComplete((e, t) -> e.fail(throwable));
    }

    @Override
    public void onComplete() {
        emitterFuture.whenComplete((e, t) -> {
            e.emit(Single.just(DataChunk.create("--boundary--".getBytes(StandardCharsets.UTF_8))));
            e.complete();
        });
    }

}

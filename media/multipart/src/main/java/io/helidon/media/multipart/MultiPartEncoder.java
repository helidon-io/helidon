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
package io.helidon.media.multipart;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * Reactive processor that encodes a stream of {@link BodyPart} into an HTTP payload.
 */
public class MultiPartEncoder implements Processor<WriteableBodyPart, DataChunk> {

    private final MessageBodyWriterContext context;
    private final String boundary;
    private final CompletableFuture<BufferedEmittingPublisher<Publisher<DataChunk>>> initFuture;
    private BufferedEmittingPublisher<Publisher<DataChunk>> emitter;
    private Subscriber<? super DataChunk> downstream;
    private Subscription upstream;

    /**
     * Create a multipart encoder.
     *
     * @param boundary boundary delimiter
     * @param context writer context
     */
    MultiPartEncoder(String boundary, MessageBodyWriterContext context) {
        Objects.requireNonNull(boundary, "boundary cannot be null!");
        Objects.requireNonNull(context, "context cannot be null!");
        this.context = context;
        this.boundary = boundary;
        initFuture = new CompletableFuture<BufferedEmittingPublisher<Publisher<DataChunk>>>();
    }

    /**
     * Create a multipart encoder.
     *
     * @param boundary boundary delimiter
     * @param context writer context
     * @return MultiPartEncoder
     */
    public static MultiPartEncoder create(String boundary, MessageBodyWriterContext context) {
        return new MultiPartEncoder(boundary, context);
    }

    @Override
    public void subscribe(Subscriber<? super DataChunk> subscriber) {
        Objects.requireNonNull(subscriber);
        if (this.emitter != null || this.downstream != null) {
            subscriber.onSubscribe(SubscriptionHelper.CANCELED);
            subscriber.onError(new IllegalStateException("Only one Subscriber allowed"));
            return;
        }
        this.downstream = subscriber;
        deferredInit();
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        SubscriptionHelper.validate(this.upstream, subscription);
        this.upstream = subscription;
        deferredInit();
    }

    private void deferredInit() {
        if (upstream != null && downstream != null) {
            emitter = BufferedEmittingPublisher.create();
            // relay request to upstream, already reduced by flatmap
            emitter.onRequest((r, t) -> upstream.request(r));
            Multi.from(emitter)
                    .flatMap(Function.identity())
                    .onCompleteResume(DataChunk.create(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8)))
                    .subscribe(downstream);
            initFuture.complete(emitter);
            downstream = null;
        }
    }

    @Override
    public void onNext(final WriteableBodyPart bodyPart) {
        emitter.emit(createBodyPartPublisher(bodyPart));
    }

    @Override
    public void onError(final Throwable throwable) {
        Objects.requireNonNull(throwable);
        initFuture.whenComplete((e, t) -> e.fail(throwable));
    }

    @Override
    public void onComplete() {
        initFuture.whenComplete((e, t) -> e.complete());
    }

    private Publisher<DataChunk> createBodyPartPublisher(final WriteableBodyPart bodyPart) {
        // start boundary
        StringBuilder sb = new StringBuilder("--").append(boundary).append("\r\n");

        // headers lines
        Map<String, List<String>> headers = bodyPart.headers().toMap();
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
                bodyPart.content().init(context)),
                // Part postfix
                Single.just(DataChunk.create("\n".getBytes(StandardCharsets.UTF_8))));
    }
}

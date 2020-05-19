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
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * Reactive processor that encodes a stream of {@link BodyPart} into an HTTP payload.
 */
public final class MultiPartEncoder implements Processor<WriteableBodyPart, DataChunk> {

    private Subscription upstream;

    /**
     * The writer context.
     */
    private final MessageBodyWriterContext context;

    /**
     * The boundary used for the generated multi-part message.
     */
    private final String boundary;

    private Subscriber<? super DataChunk> subscriber;
    private final CompletableFuture<EmittingPublisher<Publisher<DataChunk>>> emitterFuture = new CompletableFuture<>();
    private EmittingPublisher<Publisher<DataChunk>> emitter;

    /**
     * Create a new multipart encoder.
     * @param boundary boundary string
     * @param context writer context
     */
    private MultiPartEncoder(String boundary, MessageBodyWriterContext context) {
        Objects.requireNonNull(boundary, "boundary cannot be null!");
        Objects.requireNonNull(context, "context cannot be null!");
        this.context = context;
        this.boundary = boundary;
    }

    /**
     * Create a new encoder instance.
     * @param boundary multipart boundary delimiter
     * @param context writer context
     * @return MultiPartEncoder
     */
    public static MultiPartEncoder create(String boundary, MessageBodyWriterContext context) {
        return new MultiPartEncoder(boundary, context);
    }

    @Override
    public void subscribe(Subscriber<? super DataChunk> subscriber) {
        Objects.requireNonNull(subscriber);
        this.subscriber = subscriber;
        deferredInit();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        SubscriptionHelper.validate(upstream, subscription);
        this.upstream = subscription;
        deferredInit();
    }

    private void deferredInit() {
        if (upstream != null && subscriber != null) {
            emitter = EmittingPublisher.create();
            // relay request to upstream, already reduced by flatmap
            emitter.onRequest(upstream::request);
            Multi.from(emitter)
                    .flatMap(Function.identity())
                    .subscribe(subscriber);
            emitterFuture.complete(emitter);
        }
    }

    @Override
    public void onNext(WriteableBodyPart bodyPart) {
        emitter.emit(createBodyPartPublisher(bodyPart));
    }

    private Publisher<DataChunk> createBodyPartPublisher(final WriteableBodyPart bodyPart) {
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
        return Multi.concat(
            // Part prefix
            Single.just(DataChunk.create(sb.toString().getBytes(StandardCharsets.UTF_8))),
            // Part body
            bodyPart.content().toPublisher(context),
            // Part postfix
            Single.just(DataChunk.create("\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public void onError(Throwable error) {
        if (upstream != SubscriptionHelper.CANCELED) {
            upstream = SubscriptionHelper.CANCELED;
            emitterFuture.whenComplete((e, t) -> e.fail(error));
        }
    }

    @Override
    public void onComplete() {
       emitterFuture.whenComplete((e, t) -> {
            e.emit(Single.just(DataChunk.create(MimeParser.getBytes("--" + boundary + "--"))));
            e.complete();
        });
    }
}

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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.multipart.VirtualBuffer.BufferEntry;

/**
 * Reactive processor that decodes HTTP payload as a stream of {@link BodyPart}.
 */
public class MultiPartDecoder implements Processor<DataChunk, ReadableBodyPart> {

    private Subscription upstream;
    private Subscriber<? super ReadableBodyPart> downstream;
    private BufferedEmittingPublisher<ReadableBodyPart> emitter;
    private ReadableBodyPart.Builder bodyPartBuilder;
    private ReadableBodyPartHeaders.Builder bodyPartHeaderBuilder;
    private BufferedEmittingPublisher<DataChunk> bodyPartPublisher;
    private final CompletableFuture<BufferedEmittingPublisher<ReadableBodyPart>> initFuture;
    private final LinkedList<ReadableBodyPart> bodyParts;
    private final HashMap<Integer, DataChunk> chunksByIds;
    private final MimeParser parser;
    private final ParserEventProcessor parserEventProcessor;
    private final MessageBodyReaderContext context;

    /**
     * Create a new multipart decoder.
     *
     * @param boundary boundary delimiter
     * @param context reader context
     */
    MultiPartDecoder(String boundary, MessageBodyReaderContext context) {
        Objects.requireNonNull(boundary, "boundary cannot be null!");
        Objects.requireNonNull(context, "context cannot be null!");
        this.context = context;
        parserEventProcessor = new ParserEventProcessor();
        parser = new MimeParser(boundary, parserEventProcessor);
        initFuture = new CompletableFuture<>();
        bodyParts = new LinkedList<>();
        chunksByIds = new HashMap<>();
    }

    /**
     * Create a new multipart decoder.
     *
     * @param boundary boundary delimiter
     * @param context reader context
     * @return MultiPartDecoder
     */
    public static MultiPartDecoder create(String boundary, MessageBodyReaderContext context) {
        return new MultiPartDecoder(boundary, context);
    }

    @Override
    public void subscribe(Subscriber<? super ReadableBodyPart> subscriber) {
        Objects.requireNonNull(subscriber);
        if (emitter != null || downstream != null) {
            subscriber.onSubscribe(SubscriptionHelper.CANCELED);
            subscriber.onError(new IllegalStateException("Only one Subscriber allowed"));
            return;
        }
        this.downstream = subscriber;
        deferredInit();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        SubscriptionHelper.validate(upstream, subscription);
        this.upstream = subscription;
        deferredInit();
    }

    @Override
    public void onNext(DataChunk chunk) {
        try {
            ByteBuffer[] byteBuffers = chunk.data();
            for (int i = 0; i < byteBuffers.length; i++) {
                int id = parser.offer(byteBuffers[i]);
                // record the chunk using the id of the last buffer
                if (i == byteBuffers.length - 1) {
                    chunksByIds.put(id, chunk);
                }
            }
            parser.parse();
        } catch (MimeParser.ParsingException ex) {
            emitter.fail(ex);
            chunk.release();
            releaseChunks();
        }

        // submit parsed parts
        while (!bodyParts.isEmpty()) {
            if (emitter.isCancelled()) {
                return;
            }
            emitter.emit(bodyParts.poll());
        }

        // complete the parts publisher
        if (parserEventProcessor.isCompleted()) {
            emitter.complete();
            // parts are delivered sequentially
            // we potentially drop the last part if not requested
            emitter.clearBuffer(this::drainPart);
            releaseChunks();
        }

        // request more data to detect the next part
        // if not in the middle of a part content
        // or if the part content subscriber needs more
        if (upstream != SubscriptionHelper.CANCELED
                && emitter.hasRequests()
                && parserEventProcessor.isDataRequired()
                && (!parserEventProcessor.isContentDataRequired() || bodyPartPublisher.hasRequests())) {

            upstream.request(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable);
        initFuture.whenComplete((e, t) -> e.fail(throwable));
    }

    @Override
    public void onComplete() {
        initFuture.whenComplete((e, t) -> {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                try {
                    parser.close();
                } catch (MimeParser.ParsingException ex) {
                    emitter.fail(ex);
                    releaseChunks();
                }
            }
        });
    }

    private void deferredInit() {
        if (upstream != null && downstream != null) {
            emitter = BufferedEmittingPublisher.create();
            emitter.onRequest(this::onPartRequest);
            emitter.onEmit(this::drainPart);
            //emitter.onCancel(this::onPartCancel);
            emitter.subscribe(downstream);
            initFuture.complete(emitter);
            downstream = null;
        }
    }

    private void onPartRequest(long requested, long total) {
        // require more raw chunks to decode if the decoding has not
        // yet started or if more data is required to make progress
        if (!parserEventProcessor.isStarted() || parserEventProcessor.isDataRequired()) {
            upstream.request(1);
        }
    }

    private void onPartCancel() {
        emitter.clearBuffer(this::drainPart);
        releaseChunks();
    }

    private void releaseChunks() {
        Iterator<DataChunk> it = chunksByIds.values().iterator();
        while (it.hasNext()) {
            DataChunk next = it.next();
            next.release();
            it.remove();
        }
    }

    private void drainPart(ReadableBodyPart part) {
        part.content().subscribe(new Subscriber<DataChunk>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(DataChunk item) {
                item.release();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private ReadableBodyPart createPart() {
        ReadableBodyPartHeaders headers = bodyPartHeaderBuilder.build();

        // create a reader context for the part
        MessageBodyReaderContext partContext = MessageBodyReaderContext.create(context,
                /* eventListener */ null, headers, Optional.of(headers.contentType()));

        // create a readable content for the part
        MessageBodyReadableContent partContent = MessageBodyReadableContent.create(bodyPartPublisher,
                partContext);

        return bodyPartBuilder
                .headers(headers)
                .content(partContent)
                .build();
    }

    private BodyPartChunk createPartChunk(BufferEntry entry) {
        ByteBuffer data = entry.buffer();
        int id = entry.id();
        DataChunk chunk = chunksByIds.get(id);
        if (chunk == null) {
            throw new IllegalStateException("Parent chunk not found, id=" + id);
        }
        ByteBuffer[] originalBuffers = chunk.data();
        boolean release = data.limit() == originalBuffers[originalBuffers.length - 1].limit();
        if (release) {
            chunksByIds.remove(id);
        }
        return new BodyPartChunk(data, release ? chunk : null);
    }

    private final class ParserEventProcessor implements MimeParser.EventProcessor {

        private MimeParser.ParserEvent lastEvent = null;

        @Override
        public void process(MimeParser.ParserEvent event) {
            MimeParser.EventType eventType = event.type();
            switch (eventType) {
                case START_PART:
                    bodyPartPublisher = BufferedEmittingPublisher.create();
                    bodyPartHeaderBuilder = ReadableBodyPartHeaders.builder();
                    bodyPartBuilder = ReadableBodyPart.builder();
                    break;
                case HEADER:
                    MimeParser.HeaderEvent headerEvent = event.asHeaderEvent();
                    bodyPartHeaderBuilder.header(headerEvent.name(), headerEvent.value());
                    break;
                case END_HEADERS:
                    bodyParts.add(createPart());
                    break;
                case CONTENT:
                    bodyPartPublisher.emit(createPartChunk(event.asContentEvent().content()));
                    break;
                case END_PART:
                    bodyPartPublisher.complete();
                    bodyPartPublisher = null;
                    bodyPartHeaderBuilder = null;
                    bodyPartBuilder = null;
                    break;
                default:
                // nothing to do
            }
            lastEvent = event;
        }

        /**
         * Indicate if the parser has received any data.
         *
         * @return {@code true} if the parser has been offered data,
         * {@code false} otherwise
         */
        boolean isStarted() {
            return lastEvent != null;
        }

        /**
         * Indicate if the parser has reached the end of the message.
         *
         * @return {@code true} if completed, {@code false} otherwise
         */
        boolean isCompleted() {
            return lastEvent.type() == MimeParser.EventType.END_MESSAGE;
        }

        /**
         * Indicate if the parser requires more data to make progress.
         *
         * @return {@code true} if more data is required, {@code false}
         * otherwise
         */
        boolean isDataRequired() {
            return lastEvent.type() == MimeParser.EventType.DATA_REQUIRED;
        }

        /**
         * Indicate if more content data is required.
         *
         * @return {@code true} if more content data is required, {@code false}
         * otherwise
         */
        boolean isContentDataRequired() {
            return isDataRequired() && lastEvent.asDataRequiredEvent().isContent();
        }
    }
}

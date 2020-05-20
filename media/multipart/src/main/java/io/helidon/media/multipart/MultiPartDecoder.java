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
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.common.reactive.RequestedCounter;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.multipart.VirtualBuffer.BufferEntry;

/**
 * Reactive processor that decodes HTTP payload as a stream of {@link BodyPart}.
 */
public final class MultiPartDecoder implements Processor<DataChunk, ReadableBodyPart> {

    /**
     * Indicate that the chunks subscription is complete.
     */
    private boolean complete;

    /**
     * The underlying publisher.
     */
    private final EmittingPublisher<ReadableBodyPart> emitter;

    /**
     * Emitter future to handle deferred initialization of the processor.
     */
    private final CompletableFuture<EmittingPublisher<ReadableBodyPart>> emitterFuture;

    /**
     * The upstream subscription.
     */
    private Subscription upstream;

    /**
     * The downstream subscriber.
     */
    private Subscriber<? super ReadableBodyPart> downstream;

    /**
     * The builder for the current {@link BodyPart}.
     */
    private ReadableBodyPart.Builder bodyPartBuilder;

    /**
     * The builder for the current {@link ReadableBodyPartHeaders}.
     */
    private ReadableBodyPartHeaders.Builder bodyPartHeaderBuilder;

    /**
     * The publisher for the current part.
     */
    private BodyPartContentPublisher bodyPartPublisher;

    /**
     * The original chunks by ids.
     */
    private final HashMap<Integer, DataChunk> chunksByIds;

    /**
     * The MIME parser.
     */
    private final MimeParser parser;

    /**
     * The parser event processor.
     */
    private final ParserEventProcessor parserEventProcessor;

    /**
     * The bodyParts processed during each {@code onNext}.
     */
    private final LinkedList<ReadableBodyPart> bodyParts;

    /**
     * The reader context.
     */
    private final MessageBodyReaderContext context;

    /**
     * Create a new multipart decoder.
     *
     * @param boundary boundary delimiter
     * @param context reader context
     */
    private MultiPartDecoder(String boundary, MessageBodyReaderContext context) {
        Objects.requireNonNull(boundary, "boundary cannot be null!");
        Objects.requireNonNull(context, "context cannot be null!");
        this.context = context;
        parserEventProcessor = new ParserEventProcessor();
        parser = new MimeParser(boundary, parserEventProcessor);
        emitter = EmittingPublisher.create();
        emitter.onRequest(this::onRequested);
        emitterFuture = new CompletableFuture<>();
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
        if (this.downstream != null) {
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

    private void deferredInit() {
        if (upstream != null && downstream != null) {
            emitter.subscribe(downstream);
            emitterFuture.complete(emitter);
        }
    }

    @Override
    public void onNext(DataChunk chunk) {
        try {
            int id = parser.offer(chunk.data());
            chunksByIds.put(id, chunk);
            parser.parse();
        } catch (MimeParser.ParsingException ex) {
            emitter.fail(ex);
            chunk.release();
            releaseChunks();
        }

        // submit parsed parts
        Iterator<ReadableBodyPart> it = bodyParts.iterator();
        while (it.hasNext()) {
            ReadableBodyPart bodyPart = it.next();
            if (emitter.emit(bodyPart)) {
                it.remove();
                drainPart(bodyPart);
            }
        }

        // complete the subscriber
        if (parserEventProcessor.isCompleted()) {
            complete = true;
            emitter.complete();
            releaseChunks();
        }

        // request more data if not stuck at content
        // or if the part content subscriber needs more
        if (!complete
                && bodyParts.isEmpty()
                && parserEventProcessor.isDataRequired()
                && (!parserEventProcessor.isContentDataRequired() || bodyPartPublisher.requiresMoreItems())) {

            upstream.request(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable);
        emitterFuture.whenComplete((e, t) -> e.fail(throwable));
    }

    @Override
    public void onComplete() {
        emitterFuture.whenComplete((e, t) -> {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                complete = true;
                try {
                    parser.close();
                } catch (MimeParser.ParsingException ex) {
                    emitter.fail(ex);
                    releaseChunks();
                }
            }
        });
    }

    /**
     * Invoked when chunks are requested from the upstream.
     * @param n number of requested items
     */
    private void onRequested(long n) {
        // require more raw chunks to decode if the decoding has not
        // yet started or if more data is required to make progress
        if (!parserEventProcessor.isStarted() || parserEventProcessor.isDataRequired()) {
            upstream.request(1);
        }
    }

    /**
     * Release and remove all left over chunks.
     */
    private void releaseChunks() {
        Iterator<DataChunk> it = chunksByIds.values().iterator();
        while (it.hasNext()) {
            DataChunk next = it.next();
            next.release();
            it.remove();
        }
    }

    /**
     * Subscribe to a part content and drain all the data chunks.
     *
     * @param part part to drain
     */
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

    /**
     * Parser event processor.
     */
    private final class ParserEventProcessor implements MimeParser.EventProcessor {

        private MimeParser.ParserEvent lastEvent = null;

        @Override
        public void process(MimeParser.ParserEvent event) {
            MimeParser.EventType eventType = event.type();
            switch (eventType) {
                case START_PART:
                    bodyPartPublisher = new BodyPartContentPublisher();
                    bodyPartHeaderBuilder = ReadableBodyPartHeaders.builder();
                    bodyPartBuilder = ReadableBodyPart.builder();
                    break;
                case HEADER:
                    MimeParser.HeaderEvent headerEvent = event.asHeaderEvent();
                    bodyPartHeaderBuilder.header(headerEvent.name(), headerEvent.value());
                    break;
                case END_HEADERS:
                    ReadableBodyPartHeaders headers = bodyPartHeaderBuilder.build();

                    // create a reader context for the part
                    MessageBodyReaderContext partContext = MessageBodyReaderContext.create(context,
                            /* eventListener */ null, headers, Optional.of(headers.contentType()));

                    // create a readable content for the part
                    MessageBodyReadableContent partContent = MessageBodyReadableContent.create(bodyPartPublisher,
                            partContext);

                    bodyParts.add(bodyPartBuilder
                            .headers(headers)
                            .content(partContent)
                            .build());
                    break;
                case CONTENT:
                    BufferEntry entry = event.asContentEvent().content();
                    ByteBuffer data = entry.buffer();
                    int id = entry.id();
                    DataChunk chunk = chunksByIds.get(id);
                    if (chunk == null) {
                        throw new IllegalStateException("Parent chunk not found, id=" + id);
                    }
                    boolean release = data.limit() == chunk.data().limit();
                    if (release) {
                        chunksByIds.remove(id);
                    }
                    bodyPartPublisher.emit(new BodyPartChunk(data, release ? chunk : null));
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

    /**
     * Body part content publisher.
     */
    static final class BodyPartContentPublisher extends EmittingPublisher<DataChunk> {

        private final Queue<DataChunk> queue;
        private final RequestedCounter requestCounter;
        private final AtomicBoolean once = new AtomicBoolean(false);
        private boolean draining;
        private boolean completed;

        BodyPartContentPublisher() {
            super();
            onRequest(this::onRequested);
            queue = new ArrayBlockingQueue<>(256);
            requestCounter = new RequestedCounter(/* strict mode */ true);
        }

        @Override
        public void subscribe(Subscriber<? super DataChunk> subscriber) {
            if (!once.compareAndSet(false, true)) {
                subscriber.onError(new IllegalStateException("Only single subscriber is allowed!"));
            } else {
                super.subscribe(subscriber);
            }
        }

        @Override
        public boolean emit(DataChunk item) {
            if (!queue.offer(item)) {
                fail(new IllegalStateException("Unable to add an element to the body part publisher cache."));
                return false;
            }
            return drainQueue();
        }

        @Override
        public void complete() {
            completed = true;
            if (queue.isEmpty()) {
                super.complete();
            }
        }

        boolean requiresMoreItems() {
            return requestCounter.get() - queue.size() > 0;
        }

        void onRequested(long n) {
            requestCounter.increment(n, this::fail);
            drainQueue();
        }

        private boolean drainQueue() {
            if (draining || requestCounter.get() == 0L) {
                return false;
            }
            try {
                draining = true;
                requestCounter.lock();
                while (!queue.isEmpty() && requestCounter.tryDecrement()) {
                    DataChunk value = queue.poll();
                    try {
                        if (!super.emit(value) || isCancelled()) {
                            return false;
                        }
                    } catch (Throwable ex) {
                        fail(ex);
                        return false;
                    }
                }
                if (completed && queue.isEmpty()) {
                    super.complete();
                }
                return true;
            } finally {
                draining = false;
                requestCounter.unlock();
            }
        }
    }
}

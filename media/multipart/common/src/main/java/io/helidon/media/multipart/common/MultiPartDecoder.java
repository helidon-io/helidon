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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.OriginThreadPublisher;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.multipart.common.VirtualBuffer.BufferEntry;

/**
 * Reactive processor that decodes HTTP payload as a stream of {@link BodyPart}.
 * This processor is a single use publisher that supports a single subscriber,
 * it is not resumable.
 */
public final class MultiPartDecoder implements Processor<DataChunk, ReadableBodyPart> {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(MultiPartDecoder.class.getName());

    /**
     * Indicate that the chunks subscription is complete.
     */
    private boolean complete;

    /**
     * The upstream subscription.
     */
    private Subscription upstream;

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
    private final Queue<ReadableBodyPart> bodyParts;

    /**
     * The reader context.
     */
    private final MessageBodyReaderContext context;

    /**
     * The downstream publisher.
     */
    private final BodyPartPublisher downstream;

    /**
     * Create a new instance.
     *
     * @param boundary mime message boundary
     * @param context reader context
     */
    private MultiPartDecoder(String boundary, MessageBodyReaderContext context) {
        Objects.requireNonNull(boundary, "boundary cannot be null!");
        Objects.requireNonNull(context, "context cannot be null!");
        this.context = context;
        parserEventProcessor = new ParserEventProcessor();
        parser = new MimeParser(boundary, parserEventProcessor);
        downstream = new BodyPartPublisher();
        bodyParts = new LinkedList<>();
        chunksByIds = new HashMap<>();
    }

    /**
     * Create a new multipart decoder.
     * @param boundary boundary string
     * @param context reader context
     * @return MultiPartDecoder
     */
    public static MultiPartDecoder create(String boundary, MessageBodyReaderContext context) {
        return new MultiPartDecoder(boundary, context);
    }

    @Override
    public void subscribe(Subscriber<? super ReadableBodyPart> subscriber) {
        downstream.subscribe(subscriber);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        SubscriptionHelper.validate(upstream, subscription);
        this.upstream = subscription;
    }

    @Override
    public void onNext(DataChunk chunk) {
        try {
            int id = parser.offer(chunk.data());
            chunksByIds.put(id, chunk);
            parser.makeProgress();
        } catch (MimeParser.ParsingException ex) {
            downstream.error(ex);
            chunk.release();
            releaseChunks();
        }

        // submit parsed parts
        while (!bodyParts.isEmpty()) {
            ReadableBodyPart bodyPart = bodyParts.poll();
            downstream.submit(bodyPart);
        }

        // complete the subscriber
        if (parserEventProcessor.isCompleted()) {
            downstream.complete();
            releaseChunks();
        }

        // request more data if not stuck at content
        // or if the part content subscriber needs more
        if (!complete && parserEventProcessor.isDataRequired()
                && (!parserEventProcessor.isContentDataRequired() || bodyPartPublisher.requiresMoreItems())) {

            LOGGER.fine("Requesting one more chunk from upstream");
            upstream.request(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (upstream != SubscriptionHelper.CANCELED) {
            upstream = SubscriptionHelper.CANCELED;
            downstream.error(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (upstream != SubscriptionHelper.CANCELED) {
            upstream = SubscriptionHelper.CANCELED;
            LOGGER.fine("Upstream subscription completed");
            complete = true;
            try {
                parser.close();
            } catch (MimeParser.ParsingException ex) {
                downstream.error(ex);
                releaseChunks();
            }
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
     * Body part publisher.
     */
    private final class BodyPartPublisher extends OriginThreadPublisher<ReadableBodyPart, ReadableBodyPart> {

        @Override
        protected void hookOnRequested(long n, long result) {
            // require more raw chunks to decode if the decoding has not
            // yet started or if more data is required to make progress
            if (tryAcquire() > 0
                    && (!parserEventProcessor.isStarted() || parserEventProcessor.isDataRequired())) {
                upstream.request(1);
            }
        }
    }

    /**
     * Parser event processor.
     */
    private final class ParserEventProcessor implements MimeParser.EventProcessor {

        private MimeParser.ParserEvent lastEvent = null;

        @Override
        public void process(MimeParser.ParserEvent event) {
            MimeParser.EventType eventType = event.type();
            LOGGER.fine(() -> "Parser event: " + eventType);
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
                    MessageBodyReaderContext partContext = MessageBodyReaderContext.create(context, /* eventListener */ null,
                            headers, Optional.of(headers.contentType()));

                    // create a readable content for the part
                    MessageBodyReadableContent partContent = MessageBodyReadableContent.create(bodyPartPublisher, partContext);

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
                    bodyPartPublisher.submit(new BodyPartChunk(data, release ? chunk : null));
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
    static final class BodyPartContentPublisher extends OriginThreadPublisher<DataChunk, BodyPartChunk> {

        @Override
        protected DataChunk wrap(BodyPartChunk item) {
            return item;
        }
    }
}

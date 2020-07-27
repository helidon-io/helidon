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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.SubscriptionHelper;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.multipart.VirtualBuffer.BufferEntry;

/**
 * Reactive processor that decodes HTTP payload as a stream of {@link BodyPart}.
 *
 * This implementation is documented here {@code /docs-internal/multipartdecoder.md}.
 */
public class MultiPartDecoder implements Processor<DataChunk, ReadableBodyPart> {

    private static final int DOWNSTREAM_INIT = Integer.MIN_VALUE >>> 1;
    private static final int UPSTREAM_INIT = Integer.MIN_VALUE >>> 2;
    private static final int SUBSCRIPTION_LOCK = Integer.MIN_VALUE >>> 3;
    private static final Iterator<BufferEntry> EMPTY_BUFFER_ENTRY_ITERATOR = new EmptyIterator<>();
    private static final Iterator<MimeParser.ParserEvent> EMPTY_PARSER_ITERATOR = new EmptyIterator<>();

    private volatile Subscription upstream;
    private Subscriber<? super ReadableBodyPart> downstream;
    private ReadableBodyPart.Builder bodyPartBuilder;
    private ReadableBodyPartHeaders.Builder bodyPartHeaderBuilder;
    private DataChunkPublisher bodyPartPublisher;
    private Iterator<MimeParser.ParserEvent> parserIterator = EMPTY_PARSER_ITERATOR;
    private volatile Throwable error;
    private boolean cancelled;
    private AtomicInteger contenders = new AtomicInteger(Integer.MIN_VALUE);
    private AtomicLong partsRequested = new AtomicLong();
    private final HashMap<Integer, DataChunk> chunksByIds;
    private final MimeParser parser;
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
        parser = new MimeParser(boundary);
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
        if (!halfInit(UPSTREAM_INIT)) {
            Multi.<ReadableBodyPart>error(new IllegalStateException("Only one Subscriber allowed"))
                 .subscribe(subscriber);
            return;
        }
        this.downstream = subscriber;

        // contenders < 0, so any part request will be deferred
        // drain() will not request anything from upstream until the second deferredInit() invocation witnesses
        // that upstream is set
        downstream.onSubscribe(new Subscription() {

            @Override
            public void request(long n) {
                long curr = n <= 0
                            ? partsRequested.getAndSet(-1)
                            : partsRequested.getAndUpdate(v -> Long.MAX_VALUE - v > n
                            ? v + n : v < 0 ? v : Long.MAX_VALUE);
                if (curr == 0) {
                    drain();
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
                if (partsRequested.getAndSet(-1) == 0) {
                    drain();
                }
            }
        });

        deferredInit();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (!halfInit(DOWNSTREAM_INIT)) {
            SubscriptionHelper.validate(upstream, subscription);
            return;
        }
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
                    // drain() cannot be invoked concurrently, it is safe to use HashMap
                    chunksByIds.put(id, chunk);
                }
            }
            parserIterator = parser.parseIterator();
            drain();
        } catch (MimeParser.ParsingException ex) {
            drain(ex);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable);
        error = throwable;
        if (upstream != SubscriptionHelper.CANCELED) {
            upstream = SubscriptionHelper.CANCELED;
            drain();
        }
    }

    @Override
    public void onComplete() {
        if (upstream != SubscriptionHelper.CANCELED) {
            upstream = SubscriptionHelper.CANCELED;
            drain();
        }
    }

    private boolean halfInit(int mask) {
        // Attempts to set the given init mask, if contenders is in the right state for that, and
        // reports whether the contenders was in a state where that part of init needed completing.
        int c = contenders.getAndUpdate(v -> v < 0 ? v | mask : v);
        return c < 0 && (c & mask) == 0;
    }

    private void deferredInit() {
        // deferredInit is invoked twice: onSubscribe and subscribe
        // after onSubscribe and subscribe three top bits are set
        // adding SUBSCRIPTION_LOCK for the first time sets the fourth bit
        // adding SUBSCRIPTION_LOCK for the second time clears all top bits
        // making the contenders counter 0,
        // unless there were onError, onComplete, request for parts or downstream cancellation
        if (contenders.addAndGet(SUBSCRIPTION_LOCK) > 0) {
            drainLoop();
        }
    }

    private long partsRequested() {
        // Returns a negative number, if we are serving outer Subscriber, and we can tell it may
        // not issue more requests for parts: either cancelled, or a bad request was observed.
        // Otherwise returns a positive number, if serving inner Subscriber, or actual partsRequested.
        return bodyPartPublisher != null ? 1 : partsRequested.get();
    }

    private void cleanup() {
        // drop the reference to parserIterator, but keep it safe for any later invocation of parserIterator
        parserIterator = EMPTY_PARSER_ITERATOR;
        error = null;
        downstream = null; // after cleanup no uses of downstream are reachable
        cancelled = true; // after cleanup the processor appears as cancelled
        bodyPartHeaderBuilder = null;
        bodyPartBuilder = null;
        partsRequested.set(-1);
        releaseChunks();
        parser.cleanup();
    }

    /**
     * Drain the upstream data if the contenders value is positive.
     */
    protected void drain() {
        // We do not serve the next part until the last chunk of the previous part has been consumed
        // (sent to inner Subscriber).
        // Signals to outer Subscriber are serialized with the signals to the inner Subscriber.

        // drain() is a loop that retrieves ParserEvents one by one, and transitions to the next state,
        // unless waiting on the inner or outer subscriber.

        // There are three ways to enter drain():
        // 1. We are not processing a part and an outer Subscriber has unsatisfied demand for parts
        // 2. We are processing a part and an inner Subscriber has unsatisfied demand for chunks of a part
        // 3. Upstream is delivering a DataChunk to satisfy the request from outer or inner Subscriber
        if (contenders.getAndIncrement() != 0) {
            return;
        }
        drainLoop();
    }

    /**
     * Drain the upstream data in a loop while the contenders value is positive.
     */
    protected void drainLoop() {
        for (int c = 1; c > 0; c = contenders.addAndGet(-c)) {
            drainBoth();
        }
    }

    /**
     * Drain the upstream data and signal the given error.
     *
     * @param th the error to signal
     */
    protected void drain(Throwable th) {
        error = th;
        drain();
    }

    /**
     * Drain upstream (raw) data and decoded downstream data.
     */
    protected void drainBoth() {
        if (bodyPartPublisher != null && !bodyPartPublisher.drain()) {
            return;
        }

        try {
            // Proceed to drain parserIterator only if parts or body part chunks were requested
            // ie. bodyPartPublisher != null && partsRequested > 0
            // if bodyPartPublisher != null, then we are here when inner Subscriber has unsatisfied demand
            long requested = partsRequested();
            while (requested >= 0 && parserIterator.hasNext()) {
                // It is safe to consume next ParserEvent only the right Subscriber is ready to receive onNext
                // i.e partsRequested > 0
                if (requested == 0) {
                    // This means there was an attempt to deliver onError or onComplete from upstream
                    // which are allowed to be issued without request from outer Subscriber.
                    // - partsRequested > 0 for valid requests
                    // - partsRequested < 0 cancellation or invalid request
                    // we wait until demand has been manifested and parserIterator is drained
                    return;
                }

                MimeParser.ParserEvent event = parserIterator.next();
                switch (event.type()) {
                    case START_PART:
                        bodyPartHeaderBuilder = ReadableBodyPartHeaders.builder();
                        bodyPartBuilder = ReadableBodyPart.builder();
                        break;
                    case HEADER:
                        MimeParser.HeaderEvent headerEvent = event.asHeaderEvent();
                        bodyPartHeaderBuilder.header(headerEvent.name(), headerEvent.value());
                        break;
                    case END_HEADERS:
                        bodyPartPublisher = new DataChunkPublisher();
                        downstream.onNext(createPart());
                        bodyPartHeaderBuilder = null;
                        bodyPartBuilder = null;
                        // exit the parser iterator loop
                        // the parser events processing will resume upon inner Subscriber demand
                        return;
                    case BODY:
                        Iterator<BufferEntry> bodyIterator = event.asBodyEvent().body().iterator();
                        bodyPartPublisher.nextIterator(bodyIterator);
                        if (!bodyPartPublisher.drain()) {
                            // the body was not fully drained, exit the parser iterator loop
                            // the parser events processing will resume upon inner Subscriber demand
                            return;
                        }
                        break;
                    case END_PART:
                        bodyPartPublisher.complete(null);
                        bodyPartPublisher = null;
                        requested = partsRequested.updateAndGet(v -> v == Long.MAX_VALUE || v < 0 ? v : v - 1);
                        break;
                    default:
                }
            }

            // we allow requested <= 0 to reach here, because we want to allow delivery of termination signals
            // without requests or cancellations, but ultimately need to make sure we do not request from
            // upstream, unless actual demand is observed (requested > 0)
            if (requested < 0) {
                if (cancelled) {
                    upstream.cancel();
                    cleanup();
                    return;
                }
                // now is the right time to convert a bad request into an error
                // bodyPartPublisher is null, so this error gets delivered only to outer Subscriber
                error = new IllegalArgumentException("Expecting only positive requests for parts");
            }

            // ordering the delivery of errors after the delivery of all signals that precede it
            // in the order of events emitted by the parser
            if (upstream == SubscriptionHelper.CANCELED || error != null) {
                if (error != null) {
                    if (bodyPartPublisher != null) {
                        bodyPartPublisher.complete(error);
                        bodyPartPublisher = null;
                    }
                    upstream.cancel();
                    downstream.onError(error);
                } else {
                    // parser will throw, if we attempt to close it before it finished parsing the message
                    // and onError will be delivered
                    parser.close();
                    downstream.onComplete();
                }
                cleanup();
                return;
            }

            // parserIterator is drained, drop the reference to it, but keep it safe for any later invocations
            parserIterator = EMPTY_PARSER_ITERATOR;

            if (requested > 0) {
                upstream.request(1);
            }

        } catch (MimeParser.ParsingException ex) {
            // make sure we do not interact with the parser through the iterator, and do not re-enter
            // the iterator draining loop
            parserIterator = EMPTY_PARSER_ITERATOR;
            drain(ex);
        }
    }

    private void releaseChunks() {
        Iterator<DataChunk> it = chunksByIds.values().iterator();
        while (it.hasNext()) {
            DataChunk next = it.next();
            next.release();
            it.remove();
        }
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
        // FIXME: the current resource management is not implemented properly and needs to be fixed
        boolean release = data.limit() == originalBuffers[originalBuffers.length - 1].limit();
        if (release) {
            chunksByIds.remove(id);
        }
        return new BodyPartChunk(data, release ? chunk : null);
    }

    /**
     * Inner publisher that publishes the body part as {@link DataChunk}.
     */
    protected final class DataChunkPublisher implements Publisher<DataChunk> {

        private final AtomicLong chunksRequested = new AtomicLong(Long.MIN_VALUE + 1);
        private Iterator<BufferEntry> bufferEntryIterator = EMPTY_BUFFER_ENTRY_ITERATOR;
        private boolean cancelled;
        private Subscriber<? super DataChunk> subscriber;

        @Override
        public void subscribe(Subscriber<? super DataChunk> sub) {
            if (!chunksRequested.compareAndSet(Long.MIN_VALUE + 1, Long.MIN_VALUE)) {
                Multi.<DataChunk>error(new IllegalStateException("Only one Subscriber allowed"))
                     .subscribe(subscriber);
                return;
            }

            subscriber = sub;
            sub.onSubscribe(new Subscription() {

                @Override
                public void request(long n) {
                    // Illegal n makes chunksRequested negative, which interacts with drain() to drain the
                    // entire bufferEntryIterator, and signal onError
                    long curr = n <= 0
                                ? chunksRequested.getAndSet(-1)
                                : chunksRequested.getAndUpdate(v -> Long.MAX_VALUE - v > n
                                ? v + n : v < 0 ? v == Long.MIN_VALUE ? n : v : Long.MAX_VALUE);
                    if (curr == 0) {
                        MultiPartDecoder.this.drain();
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                    // Ensure the part chunks are drained to make the next part available
                    if (chunksRequested.getAndSet(-1) == 0) {
                        MultiPartDecoder.this.drain();
                    }
                }
            });

            if (chunksRequested.compareAndSet(Long.MIN_VALUE, 0)) {
                return;
            }
            MultiPartDecoder.this.drain();
        }

        /**
         * Set the next buffer entry iterator.
         * @param iterator the iterator to set
         */
        void nextIterator(Iterator<BufferEntry> iterator) {
            // This is invoked only when the previous bufferEntryIterator has been consumed fully,
            // and chunksRequested > 0, so no one is calling drain() concurrently
            // chunksRequested is modified atomically, so any future invocation of drain() will observe
            // bufferEntryIterator normal store (bufferEntryIterator and all of its content is published safely)
            bufferEntryIterator = iterator;
        }

        /**
         * Complete the publisher.
         *
         * @param th throwable, if not {@code null} signals {@code onError}, otherwise signals {@code onComplete}
         */
        void complete(Throwable th) {
            if (chunksRequested.get() < 0) {
                if (cancelled) {
                    subscriber = null;
                    return;
                }
                th = new IllegalArgumentException("Expecting only positive requests");
            }
            cancelled = true;
            chunksRequested.set(-1);

            // bufferEntryIterator is drained because complete() is invoked only by drain() which proceeds past
            // state == BODY only when drain() returned true
            if (th != null) {
                subscriber.onError(th);
            } else {
                subscriber.onComplete();
            }
            subscriber = null;
        }

        /**
         * Drain the current buffer entry iterator according to the current request count.
         *
         * @return {@code true} if the iterator was fully drained, {@code false} otherwise
         */
        boolean drain() {
            long requested = chunksRequested.get();
            long chunksEmitted = 0;

            // requested < 0 behaves like cancel, i.e drain bufferEntryIterator
            while (chunksEmitted < requested && bufferEntryIterator.hasNext()) {
                do {
                    DataChunk chunk = createPartChunk(bufferEntryIterator.next());
                    subscriber.onNext(chunk);
                    chunk.release();
                    chunksEmitted++;
                } while (chunksEmitted < requested && bufferEntryIterator.hasNext());

                long ce = chunksEmitted;
                requested = chunksRequested.updateAndGet(v -> v == Long.MAX_VALUE || v < 0 ? v : v - ce);
                chunksEmitted = 0;
            }

            if (requested < 0) {
                while (bufferEntryIterator.hasNext()) {
                    createPartChunk(bufferEntryIterator.next()).release();
                }
            }

            if (requested != 0) {
                // bufferEntryIterator is drained, drop the reference
                bufferEntryIterator = EMPTY_BUFFER_ENTRY_ITERATOR;
                return true;
            }
            return false;
        }
    }

    private static final class EmptyIterator<T> implements Iterator<T> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new IllegalStateException("Read beyond EOF");
        }
    }
}

/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.ReactiveStreamsAdapter;

import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;

/**
 * A {@link DataChunk} collecting all chunks into a single {@code byte array} accessible using {@link #result()} method.
 */
public class CollectingSubscriber extends BaseSubscriber<DataChunk> {

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final WritableByteChannel channel = Channels.newChannel(baos);
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();

    private final long initialRequest;
    private volatile long requestedCounter;

    private volatile long onNextCounter = 0;

    public CollectingSubscriber(long initialRequest) {
        this.initialRequest = initialRequest;
    }

    public CollectingSubscriber() {
        this(Long.MAX_VALUE);
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        requestedCounter = initialRequest;
        subscription.request(initialRequest);
    }

    @Override
    protected void hookOnNext(DataChunk item) {
        onNextCounter++;
        if (requestedCounter < Long.MAX_VALUE) {
            requestedCounter--;
            if (requestedCounter <= 0) {
                requestedCounter = 1;
                request(1);
            }
        }
        if (item != null) {
            try {
                ByteBuffer data = item.data();
                if (data != null) {
                    try {
                        channel.write(data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                item.release();
            }
        }
    }

    @Override
    protected void hookOnComplete() {
        result.complete(baos.toByteArray());
    }

    @Override
    protected void hookOnError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    /**
     * Returns a collected {@code byte array}.
     *
     * @return a collected {@code byte array}
     */
    public CompletableFuture<byte[]> result() {
        return result;
    }

    public long onNextCounter() {
        return onNextCounter;
    }

    public void subscribeOn(Flow.Publisher<DataChunk> publisher) {
        ReactiveStreamsAdapter.publisherFromFlow(publisher).subscribe(this);
    }

}

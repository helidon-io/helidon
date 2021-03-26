/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;

class ByteChannelSubscriber extends CompletionSingle<Void> implements Flow.Subscriber<ByteBuffer> {
    private final ExecutorService executorService;
    private Flow.Subscription subscription;
    private final WritableByteChannel byteChannel;
    private final Executor executor;
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private volatile CompletableFuture<Void> lastWriteFinished = CompletableFuture.completedFuture(null);

    ByteChannelSubscriber(WritableByteChannel byteChannel, ExecutorService executor) {
        this.byteChannel = byteChannel;
        this.executor = executor;
        this.executorService = executor;
    }

    ByteChannelSubscriber(WritableByteChannel byteChannel, Executor executor) {
        this.byteChannel = byteChannel;
        this.executor = executor;
        this.executorService = null;
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(1L);
    }

    @Override
    public void onNext(final ByteBuffer nextByteBuffer) {
        lastWriteFinished = CompletableFuture.runAsync(() -> {
            try {
                for (;;) {
                    byteChannel.write(nextByteBuffer);
                    if (nextByteBuffer.remaining() == 0) break;
                    Thread.onSpinWait();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor)
                .exceptionally(throwable -> {
                    subscription.cancel();
                    completed.completeExceptionally(throwable);
                    return null;
                })
                .thenRun(() -> {
                    // request one by one, concurrent writes are not possible
                    subscription.request(1L);
                });
    }

    @Override
    public void onError(final Throwable throwable) {
        try {
            byteChannel.close();
        } catch (IOException e) {
            throwable.addSuppressed(e);
        }
        completed.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        lastWriteFinished.thenRun(() -> {
            try {
                byteChannel.close();
                completed.complete(null);
            } catch (IOException e) {
                completed.completeExceptionally(e);
            } finally {
                if (executorService != null) {
                    executorService.shutdown();
                }
            }
        });
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super Void> subscriber) {
        Single.create(completed, true).subscribe(subscriber);
    }
}

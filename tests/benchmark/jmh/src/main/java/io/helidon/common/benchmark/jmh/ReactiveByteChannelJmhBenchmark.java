/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.benchmark.jmh;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.IoMulti;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ReactiveByteChannelJmhBenchmark {
    private static final byte[] PAYLOAD = new byte[1024];

    @Benchmark
    public int consumeChannel(Blackhole blackhole) {
        var channel = new ByteArrayChannel();
        var subscriber = new ChannelSubscriber(blackhole, false);
        try {
            IoMulti.multiFromByteChannelBuilder(channel).build().subscribe(subscriber);
            if (subscriber.failure != null) {
                throw new IllegalStateException("Synchronous channel consumption failed", subscriber.failure);
            }
            blackhole.consume(subscriber.completed);
            blackhole.consume(channel.isOpen());
            return subscriber.consumedBytes;
        } finally {
            channel.close();
        }
    }

    @Benchmark
    public boolean cancelBeforeDemand(Blackhole blackhole) {
        var channel = new ByteArrayChannel();
        var subscriber = new ChannelSubscriber(blackhole, true);
        try {
            IoMulti.multiFromByteChannelBuilder(channel).build().subscribe(subscriber);
            blackhole.consume(subscriber.consumedBytes);
            blackhole.consume(subscriber.completed);
            blackhole.consume(subscriber.failure);
            return !channel.isOpen();
        } finally {
            channel.close();
        }
    }

    private static class ByteArrayChannel implements ReadableByteChannel {
        private int position;
        private boolean open = true;

        @Override
        public int read(ByteBuffer destination) throws ClosedChannelException {
            if (!open) {
                throw new ClosedChannelException();
            }
            if (position == PAYLOAD.length) {
                return -1;
            }
            int length = Math.min(destination.remaining(), PAYLOAD.length - position);
            destination.put(PAYLOAD, position, length);
            position += length;
            return length;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    private static class ChannelSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final Blackhole blackhole;
        private final boolean cancelOnSubscribe;

        private int consumedBytes;
        private boolean completed;
        private Throwable failure;

        private ChannelSubscriber(Blackhole blackhole, boolean cancelOnSubscribe) {
            this.blackhole = blackhole;
            this.cancelOnSubscribe = cancelOnSubscribe;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (cancelOnSubscribe) {
                subscription.cancel();
            } else {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(ByteBuffer item) {
            consumedBytes += item.remaining();
            blackhole.consume(item);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}

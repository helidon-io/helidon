/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.helidon.common.Builder;
import io.helidon.common.LazyValue;

/**
 * Create reactive stream from standard IO resources.
 */
public interface IoMulti {

    /**
     * Create an {@link java.io.OutputStream} that provides the data written
     * as a {@link Multi}.
     * <p>
     * In case there is no demand,
     * {@link OutputStream#write(byte[], int, int)} methods are blocked
     * until downstream request for more data.
     *
     * @return new {@link Multi} publisher extending {@link OutputStream}
     * @deprecated Please use {@link #outputStreamMulti()}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    static OutputStreamMulti createOutputStream() {
        return new OutputStreamMulti();
    }

    /**
     * Create an {@link java.io.OutputStream} that provides the data written
     * as a {@link Multi}.
     * <p>
     * In case there is no demand,
     * {@link OutputStream#write(byte[], int, int)} methods are blocked
     * until downstream request for more data.
     *
     * @return new {@link OutputStream} implementing {@link Multi}
     */
    static OutputStreamMulti outputStreamMulti() {
        return new OutputStreamMulti();
    }

    /**
     * Creates a builder of the {@link java.io.OutputStream} that provides data written
     * as a {@link io.helidon.common.reactive.Multi}.
     *
     * @return the builder
     * @see #outputStreamMulti()
     * @deprecated Please use {@link #outputStreamMultiBuilder()}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    static OutputStreamMultiBuilder builderOutputStream() {
        return new OutputStreamMultiBuilder();
    }

    /**
     * Creates a builder of the {@link java.io.OutputStream} that provides data written
     * as a {@link io.helidon.common.reactive.Multi}.
     *
     * @return the builder
     * @see #outputStreamMulti()
     */
    static OutputStreamMultiBuilder outputStreamMultiBuilder() {
        return new OutputStreamMultiBuilder();
    }

    /**
     * Create a {@link Multi} instance that publishes {@link ByteBuffer}s from
     * the given {@link InputStream}.
     * <p>
     * {@link InputStream} is trusted not to block on read operations, in case
     * it can't be assured use builder to specify executor for asynchronous waiting
     * for blocking reads. {@code IoMulti.builder(is).executor(executorService).build()}.
     *
     * @param inputStream the Stream to publish
     * @return Multi
     * @throws NullPointerException if {@code stream} is {@code null}
     * @deprecated please use {@link #multiFromStream(java.io.InputStream)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    static Multi<ByteBuffer> createInputStream(final InputStream inputStream) {
        return IoMulti.builderInputStream(inputStream)
                .build();
    }

    /**
     * Create a {@link Multi} instance that publishes {@link ByteBuffer}s from
     * the given {@link InputStream}.
     * <p>
     * {@link InputStream} is trusted not to block on read operations, in case
     * it can't be assured use builder to specify executor for asynchronous waiting
     * for blocking reads. {@code IoMulti.builder(is).executor(executorService).build()}.
     *
     * @param inputStream the Stream to publish
     * @return Multi
     */
    static Multi<ByteBuffer> multiFromStream(final InputStream inputStream) {
        return IoMulti.builderInputStream(inputStream)
                .build();
    }

    /**
     * Creates a builder of the {@link Multi} from supplied {@link java.io.InputStream}.
     *
     * @param inputStream the Stream to publish
     * @return the builder
     * @deprecated Please use {@link #multiFromStreamBuilder(java.io.InputStream)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    static MultiFromInputStreamBuilder builderInputStream(final InputStream inputStream) {
        Objects.requireNonNull(inputStream);
        return new MultiFromInputStreamBuilder(inputStream);
    }

    /**
     * Creates a builder of the {@link Multi} from supplied {@link java.io.InputStream}.
     *
     * @param inputStream the Stream to publish
     * @return the builder
     */
    static MultiFromInputStreamBuilder multiFromStreamBuilder(final InputStream inputStream) {
        Objects.requireNonNull(inputStream);
        return new MultiFromInputStreamBuilder(inputStream);
    }

    /**
     * Creates a multi that reads data from the provided byte channel.
     * The multi uses an executor service to process asynchronous reads.
     * You can provide a custom executor service using
     * {@link #multiFromByteChannelBuilder(java.nio.channels.ReadableByteChannel)}.
     *
     * @param byteChannel readable byte channel with data
     * @return publisher of data from the provided channel
     */
    static Multi<ByteBuffer> multiFromByteChannel(ReadableByteChannel byteChannel) {
        return multiFromByteChannelBuilder(byteChannel).build();
    }

    /**
     * Creates a builder of {@link Multi} from provided {@link java.nio.channels.ReadableByteChannel}.
     *
     * @param byteChannel readable byte channel with data
     * @return fluent API builder to configure additional details
     */
    static MultiFromByteChannelBuilder multiFromByteChannelBuilder(ReadableByteChannel byteChannel) {
        return new MultiFromByteChannelBuilder(Objects.requireNonNull(byteChannel));
    }

    /**
     * Fluent API builder for creating a {@link io.helidon.common.reactive.Multi} from an
     * {@link java.io.InputStream}.
     */
    final class MultiFromByteChannelBuilder implements Builder<Multi<ByteBuffer>> {
        private static final int DEFAULT_BUFFER_CAPACITY = 1024 * 8;
        private static final RetrySchema DEFAULT_RETRY_SCHEMA = RetrySchema.linear(0, 10, 250);

        private final ReadableByteChannel theChannel;

        private LazyValue<ScheduledExecutorService> executor = LazyValue.create(() -> Executors.newScheduledThreadPool(1));
        private RetrySchema retrySchema = DEFAULT_RETRY_SCHEMA;
        private int bufferCapacity = DEFAULT_BUFFER_CAPACITY;
        private boolean externalExecutor;

        private MultiFromByteChannelBuilder(ReadableByteChannel theChannel) {
            this.theChannel = theChannel;
        }

        @Override
        public Multi<ByteBuffer> build() {
            return new MultiFromByteChannel(this);
        }

        /**
         * Configure executor service to use for scheduling reads from the channel.
         * If an executor is configured using this method, it will not be terminated when the publisher completes.
         *
         * @param executor to use for scheduling
         * @return updated builder instance
         */
        public MultiFromByteChannelBuilder executor(ScheduledExecutorService executor) {
            Objects.requireNonNull(executor);

            this.executor = LazyValue.create(executor);
            this.externalExecutor = true;
            return this;
        }

        /**
         * Retry schema to use when reading from the channel.
         * If a channel read fails (e.g. no data is read), the read is scheduled using
         * {@link #executor} using the provided retry schema, to prolong the delays between retries.
         * <p>
         * By default the first delay is {@code 0} milliseconds, incrementing by {@code 50 milliseconds} up
         * to {@code 250} milliseconds.
         *
         * @param retrySchema schema to use
         * @return updated builder instance
         */
        public MultiFromByteChannelBuilder retrySchema(RetrySchema retrySchema) {
            Objects.requireNonNull(retrySchema);

            this.retrySchema = retrySchema;
            return this;
        }

        /**
         * Capacity of byte buffer in number of bytes.
         *
         * @param bufferCapacity capacity of the buffer, defaults to 8 Kb
         * @return updated builder instance
         */
        public MultiFromByteChannelBuilder bufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }

        ReadableByteChannel theChannel() {
            return theChannel;
        }

        LazyValue<ScheduledExecutorService> executor() {
            return executor;
        }

        RetrySchema retrySchema() {
            return retrySchema;
        }

        int bufferCapacity() {
            return bufferCapacity;
        }

        // we need to know whether to shut the executor down
        boolean isExternalExecutor() {
            return externalExecutor;
        }
    }

    /**
     * Fluent API builder for creating a {@link io.helidon.common.reactive.Multi} from an
     * {@link java.io.InputStream}.
     */
    final class MultiFromInputStreamBuilder implements Builder<Multi<ByteBuffer>> {

        private int bufferSize = 1024;
        private ExecutorService executor;
        private final InputStream inputStream;

        MultiFromInputStreamBuilder(final InputStream inputStream) {
            this.inputStream = inputStream;
        }

        /**
         * Set the size of {@link ByteBuffer}s being published.
         *
         * @param bufferSize size of the {@link ByteBuffer}
         * @return Multi
         */
        public MultiFromInputStreamBuilder byteBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * If the {@code InputStream} can block in read method, use executor for
         * asynchronous waiting.
         *
         * @param executor used for asynchronous waiting for blocking reads
         * @return this builder
         */
        public MultiFromInputStreamBuilder executor(final ExecutorService executor) {
            Objects.requireNonNull(executor);
            this.executor = executor;
            return this;
        }

        @Override
        public Multi<ByteBuffer> build() {
            if (executor != null) {
                return new MultiFromBlockingInputStream(inputStream, bufferSize, executor);
            }
            return new MultiFromInputStream(inputStream, bufferSize);
        }
    }

    final class OutputStreamMultiBuilder implements Builder<OutputStreamMulti> {

        private final OutputStreamMulti streamMulti = new OutputStreamMulti();

        private OutputStreamMultiBuilder() {
        }

        /**
         * Set max timeout for which is allowed to block write methods,
         * in case there is no demand from downstream.
         *
         * @param timeout the maximum time to block
         * @param unit    the time unit of the timeout argument
         */
        public OutputStreamMultiBuilder timeout(long timeout, TimeUnit unit) {
            streamMulti.timeout(TimeUnit.MILLISECONDS.convert(timeout, unit));
            return this;
        }

        /**
         * Callback executed when request signal from downstream arrive.
         * <ul>
         * <li><b>param</b> {@code n} the requested count.</li>
         * <li><b>param</b> {@code demand} the current total cumulative requested count,
         * ranges between [0, {@link Long#MAX_VALUE}] where the max indicates that this
         * publisher is unbounded.</li>
         * </ul>
         *
         * @param requestCallback to be executed
         */
        public OutputStreamMultiBuilder onRequest(BiConsumer<Long, Long> requestCallback) {
            streamMulti.onRequest(requestCallback);
            return this;
        }

        @Override
        public OutputStreamMulti build() {
            return streamMulti;
        }
    }
}

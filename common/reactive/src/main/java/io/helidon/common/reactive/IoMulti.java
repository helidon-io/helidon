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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.helidon.common.Builder;

/**
 * Create reactive stream from standard IO resources.
 */
public interface IoMulti {

    /**
     * Create a {@link Multi} publisher, to which is possible to publish
     * as in to an {@link OutputStream}. In case there is no demand,
     * {@link OutputStream#write(byte[], int, int)} methods are blocked
     * until downstream request for more data.
     *
     * @return new {@link Multi} publisher extending {@link OutputStream}
     */
    static OutputStreamMulti create() {
        return new OutputStreamMulti();
    }

    /**
     * Creates a builder of the {@link Multi} publisher, to which is possible
     * to publish as in to an {@link OutputStream}. In case there is no demand,
     * {@link OutputStream#write(byte[], int, int)} methods are blocked
     * until downstream request for more data.
     *
     * @return the builder
     */
    static OutputStreamMultiBuilder builder() {
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
     */
    static Multi<ByteBuffer> create(final InputStream inputStream) {
        return IoMulti.builder(inputStream)
                .build();
    }

    /**
     * Creates a builder of the {@link Multi} from supplied {@link java.io.InputStream}.
     *
     * @param inputStream the Stream to publish
     * @return the builder
     */
    static MultiFromInputStreamBuilder builder(final InputStream inputStream) {
        Objects.requireNonNull(inputStream);
        return new MultiFromInputStreamBuilder(inputStream);
    }

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
        public OutputStreamMultiBuilder onRequest(BiConsumer<Long, Long> requestCallback){
            streamMulti.onRequest(requestCallback);
            return this;
        }

        @Override
        public OutputStreamMulti build() {
            return streamMulti;
        }
    }
}

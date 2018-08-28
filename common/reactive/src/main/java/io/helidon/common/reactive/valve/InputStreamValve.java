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

package io.helidon.common.reactive.valve;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * The InputStreamValve is a {@link ByteBuffer} based {@link Valve} that transforms
 * a possibly blocking {@link InputStream} into the Valve.
 */
public class InputStreamValve extends RetryingPausableRegistry<ByteBuffer> implements Valve<ByteBuffer> {
    private final InputStream stream;
    private final int bufferSize;

    InputStreamValve(InputStream stream, int bufferSize) {
        this.stream = stream;
        this.bufferSize = bufferSize;
    }

    @Override
    protected ByteBuffer moreData() throws Throwable {
        byte[] bytes = new byte[bufferSize];

        int len = stream.read(bytes);
        return len != -1 ? ByteBuffer.wrap(bytes, 0, len) : null;
    }

    static class InputStreamExecutorValve extends InputStreamValve {

        private final ExecutorService executorService;

        InputStreamExecutorValve(InputStream stream, int bufferSize, ExecutorService executorService) {
            super(stream, bufferSize);
            this.executorService = executorService;
        }

        @Override
        protected void tryProcess() {
            executorService.submit(() -> {
                super.tryProcess();
            });
        }
    }

    /**
     * A collector of {@link ByteBuffer} instances into a {@link String} of the provided
     * charset.
     *
     * @param charset the desired charset of the returned string
     * @return a string representation of the collected byte buffers
     */
    public static Collector<ByteBuffer, ByteArrayOutputStream, String> byteBufferStringCollector(Charset charset) {
        return Collectors.collectingAndThen(byteBufferByteArrayCollector(), bytes -> new String(bytes, charset));
    }

    /**
     * A collector of {@link ByteBuffer} instances into a single {@link ByteBuffer} instance.
     *
     * @return a single byte buffer from the collected byte buffers
     */
    public static Collector<ByteBuffer, ByteArrayOutputStream, ByteBuffer> byteBuffer2Collector() {
        return Collectors.collectingAndThen(byteBufferByteArrayCollector(), ByteBuffer::wrap);
    }

    /**
     * A collector of {@link ByteBuffer} instances into a single byte array.
     *
     * @return a single byte array from the collected byte buffers
     */
    public static Collector<ByteBuffer, ByteArrayOutputStream, byte[]> byteBufferByteArrayCollector() {

        return Collector.of(ByteArrayOutputStream::new,
                            (stream, byteBuffer) -> {
                                try {
                                    synchronized (stream) {
                                        WritableByteChannel channel = Channels.newChannel(stream);
                                        channel.write(byteBuffer);
                                    }
                                } catch (IOException e) {
                                    // not expected to be thrown because we're operating in memory only
                                    throw new IllegalStateException("This exception is never expected.", e);
                                }
                            },
                            (stream, stream2) -> {
                                try {
                                    synchronized (stream) {
                                        stream2.writeTo(stream);
                                    }
                                    return stream;
                                } catch (IOException e) {
                                    // not expected to be thrown because we're operating in memory only
                                    throw new IllegalStateException("This exception is never expected.", e);
                                }
                            },
                            ByteArrayOutputStream::toByteArray);
    }

}

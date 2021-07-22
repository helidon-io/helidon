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
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class ByteChannelSubscriberTest {

    final static List<String> EXPECTED_VALUES = List.of("line 1", "line 2", "line 3", "line 4", "line 5", "line 6", "line 7");

    @Test
    void writeToFile() throws IOException {
        Path filePath = Files.createTempFile("writeToFile", ".tmp");
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE);
        Multi.create(EXPECTED_VALUES)
                .map(line -> line + "\n")
                .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)))
                .to(IoMulti.multiToByteChannel(fileChannel))
                .await();

        assertThat(Arrays.asList(Files.readString(filePath).split("\n")), Matchers.contains(EXPECTED_VALUES.toArray(new String[0])));
    }

    @Test
    void shutdownDefaultExecutor() throws IOException, InterruptedException {
        Path filePath = Files.createTempFile("writeToFile", ".tmp");
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE);
        ExecutorService defaultExecutor = Executors.newSingleThreadExecutor();
        try {
            ByteChannelSubscriber byteChannelSubscriber = new ByteChannelSubscriber(fileChannel, defaultExecutor);
            Multi.create(EXPECTED_VALUES)
                    .map(line -> line + "\n")
                    .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)))
                    .to(m -> {
                        m.subscribe(byteChannelSubscriber);
                        return byteChannelSubscriber;
                    })
                    .await();
        } finally {
            assertThat("Default executor should have been shutdown on complete.",
                    defaultExecutor.awaitTermination(500, TimeUnit.MILLISECONDS));
        }
        assertThat(Arrays.asList(Files.readString(filePath).split("\n")), Matchers.contains(EXPECTED_VALUES.toArray(new String[0])));
    }

    @Test
    void writeToFileWithSuppliedExecutor() throws IOException {
        Path filePath = Files.createTempFile("writeToFile", ".tmp");
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE);
        ExecutorService customExecutor = Executors.newSingleThreadExecutor();
        try {
            Multi.create(EXPECTED_VALUES)
                    .map(line -> line + "\n")
                    .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)))
                    .to(IoMulti.multiToByteChannelBuilder(fileChannel)
                            .executor(customExecutor)
                            .build())
                    .await();
        } finally {
            customExecutor.shutdown();
        }
        assertThat(Arrays.asList(Files.readString(filePath).split("\n")), Matchers.contains(EXPECTED_VALUES.toArray(new String[0])));
    }

    @Test
    void writePartToFileBeforeError() throws IOException {
        RuntimeException expectedError = new RuntimeException("BOOM!");
        AtomicReference<Throwable> resultError = new AtomicReference<>();
        Path filePath = Files.createTempFile("writePartToFileBeforeError", ".tmp");
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE);
        Multi.create(EXPECTED_VALUES)
                .peek(line -> {
                    if (line.endsWith("3")) throw expectedError;
                })
                .map(line -> line + "\n")
                .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)))
                .to(IoMulti.multiToByteChannel(fileChannel))
                .onErrorResumeWithSingle(e -> {
                    resultError.set(e);
                    return Single.empty();
                })
                .await();

        assertThat(resultError.get(), CoreMatchers.equalTo(expectedError));
        assertThat(Arrays.asList(Files.readString(filePath).split("\n")), Matchers.contains(EXPECTED_VALUES.subList(0, 2).toArray(new String[0])));
    }

    @Test
    void closesChannel() {
        ArrayList<String> result = new ArrayList<>(EXPECTED_VALUES.size());
        AtomicBoolean closed = new AtomicBoolean(false);
        WritableByteChannel fileChannel = new WritableByteChannel() {
            @Override
            public int write(final ByteBuffer src) {
                byte[] array = new byte[src.remaining()];
                src.get(array);
                result.add(new String(array));
                return array.length;
            }

            @Override
            public boolean isOpen() {
                return !closed.get();
            }

            @Override
            public void close() throws IOException {
                closed.set(true);
            }
        };
        Multi.create(EXPECTED_VALUES)
                .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)))
                .to(IoMulti.multiToByteChannel(fileChannel))
                .await();

        assertThat("WritableByteChannel should have been closed when stream completed.", closed.get());
        assertThat(result, Matchers.contains(EXPECTED_VALUES.toArray(new String[0])));
    }

    @Test
    void readBytesOneByOne() {
        String expected = String.join("", EXPECTED_VALUES);
        ByteBuffer result = ByteBuffer.allocate(expected.getBytes(StandardCharsets.UTF_8).length);
        AtomicBoolean closed = new AtomicBoolean(false);
        WritableByteChannel fileChannel = new WritableByteChannel() {

            final AtomicReference<ByteBuffer> lastBuffer = new AtomicReference<>();

            @Override
            public int write(final ByteBuffer src) {
                if (src.hasRemaining()) {
                    lastBuffer
                            .updateAndGet(old -> old == null ? ByteBuffer.allocate(src.remaining()) : old)
                            .put(src.get());
                    if (!src.hasRemaining()) {
                        result.put(lastBuffer.getAndSet(null).flip());
                    }
                    return 1;
                }
                return 0;
            }

            @Override
            public boolean isOpen() {
                return !closed.get();
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        Multi.create(EXPECTED_VALUES)
                .map(s -> ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)))
                .to(IoMulti.multiToByteChannel(fileChannel))
                .await();

        assertThat("WritableByteChannel should have been closed when stream completed.", closed.get());
        assertThat(new String(result.flip().array()), Matchers.equalTo(expected));
    }
}

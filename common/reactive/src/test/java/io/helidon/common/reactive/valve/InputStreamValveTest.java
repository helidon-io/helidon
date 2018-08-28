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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.hamcrest.Matcher;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.AnyOf;
import org.hamcrest.core.Every;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.StringStartsWith;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The InputStreamValveTest.
 */
class InputStreamValveTest {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(r -> new Thread(r) {{
        setName("test-thread");
    }});
    private static final int LARGE_DATA_YIELDING_SIZE = 10000;
    private static final int LARGE_DATA_SIZE = 100000;

    @Test
    void sameThreadAscii() throws Exception {
        String string = "qwertyuiopasdfghjklzxcvbnm1234567890";

        verifyFromString(string, StandardCharsets.US_ASCII);
    }

    @Test
    void sameThreadLargeAscii() throws Exception {
        String string = "qwertyuiopasdfghjklzxcvbnm1234567890";
        StringBuilder stringBuilder = new StringBuilder(LARGE_DATA_SIZE * string.length());
        for (int i = 0; i < LARGE_DATA_SIZE; i++) {
            stringBuilder.append(string);
        }
        verifyFromString(stringBuilder.toString(), StandardCharsets.US_ASCII, null, 8 * 1024, 441, "main");
    }

    @Test
    void sameThreadResumedYieldingLargeAscii() throws Exception {
        verifyOnExecutorService(null);
    }

    @Test
    void onExecutorYieldingLargeAscii() throws Exception {
        AtomicLong counter = new AtomicLong(1);
        verifyOnExecutorService(Executors.newFixedThreadPool(16,
                                          r -> new Thread(r::run,
                                                          "test-thread-" + counter
                                                                  .getAndIncrement())));
    }

    private void verifyOnExecutorService(ExecutorService executorService) throws Exception {
        String string = "qwertyuiopasdfghjklzxcvbnm1234567890";
        StringBuilder stringBuilder = new StringBuilder(LARGE_DATA_YIELDING_SIZE * string.length());
        for (int i = 0; i < LARGE_DATA_YIELDING_SIZE; i++) {
            stringBuilder.append(string);
        }

        AtomicBoolean completed = new AtomicBoolean(false);

        Function<InputStream, Valve<ByteBuffer>> valveFunction = stream -> {
            Valve<ByteBuffer> valve;
            if (executorService != null) {
                valve = new InputStreamValve
                        .InputStreamExecutorValve(stream, 1024, executorService) {
                    @Override
                    protected ByteBuffer moreData() throws Throwable {
                        Thread.yield();
                        return super.moreData();
                    }
                };
            } else {
                valve = new InputStreamValve(stream, 1024) {
                    @Override
                    protected ByteBuffer moreData() throws Throwable {
                        Thread.yield();
                        return super.moreData();
                    }
                };
            }

            Executors.newSingleThreadExecutor(r -> new Thread(r, "test-thread-resuming")).submit(() -> {
                while (!completed.get()) {
                    valve.pause();
                    Thread.yield();
                    valve.resume();
                    Thread.yield();
                }
            });

            return valve;
        };
        try {
            Matcher<String> itemMatcher = StringStartsWith.startsWith("test-thread-");
            if (executorService == null) {
                itemMatcher = AnyOf.anyOf(itemMatcher, Is.is("main"));
            }
            verifyFromString(stringBuilder.toString(), StandardCharsets.US_ASCII, 353,
                             valveFunction, Every.everyItem(itemMatcher));
        } finally {
            completed.set(true);
        }
    }

    @Test
    void onExecutorAscii() throws Exception {
        String string = "qwertyuiopasdfghjklzxcvbnm1234567890";

        verifyFromString(string, StandardCharsets.US_ASCII, EXECUTOR_SERVICE, 64, 2, "test-thread");
    }

    @Test
    void sameThreadUTF_8() throws Exception {
        String string = "asdf+ěščŘŽÝÁ";

        verifyFromString(string, StandardCharsets.UTF_8);
    }

    private void verifyFromString(String string,
                                  Charset charset,
                                  ExecutorService executorService,
                                  int bufferSize,
                                  int expectedCallCount,
                                  String... threadNames) throws Exception {
        verifyFromString(string,
                         charset,
                         expectedCallCount,
                         stream -> Valves.from(stream, bufferSize, executorService),
                         threadNames);
    }

    private void verifyFromString(String string, final Charset charset) throws Exception {
        verifyFromString(string, charset, 2, stream -> Valves.from(stream, 64, null), "main");
    }

    @SuppressWarnings("unchecked")
    private void verifyFromString(String string,
                                  final Charset charset,
                                  int expected,
                                  Function<InputStream, Valve<ByteBuffer>> valveFunction,
                                  String... items) throws Exception {
        verifyFromString(string, charset, expected, valveFunction,
                         AllOf.allOf((Matcher) IsCollectionWithSize.hasSize(items.length),
                                     IsCollectionContaining.hasItems(items)));
    }

    @SuppressWarnings("unchecked")
    private void verifyFromString(String string,
                                  final Charset charset,
                                  int expected,
                                  Function<InputStream, Valve<ByteBuffer>> valveFunction,
                                  Matcher matcher) throws Exception {
        AtomicLong readCounter = new AtomicLong();
        Set<String> threadNames = new HashSet<>();

        ByteArrayInputStream stream = new ByteArrayInputStream(string.getBytes(charset)) {
            @Override
            public int read(byte[] b) throws IOException {
                readCounter.incrementAndGet();
                threadNames.add(Thread.currentThread().getName());

                return super.read(b);
            }
        };

        Valve<ByteBuffer> valve = valveFunction.apply(stream);

        String result = valve.collect(InputStreamValve.byteBufferStringCollector(charset))
                             .toCompletableFuture()
                             .get();

        assertEquals(string, result);
        assertEquals(expected, readCounter.intValue());
        assertThat("Unexpected thread names: " + threadNames, threadNames, matcher);
    }

}

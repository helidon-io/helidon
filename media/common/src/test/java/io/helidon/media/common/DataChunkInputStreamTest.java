/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.OriginThreadPublisher;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link DataChunkInputStream}.
 */
public class DataChunkInputStreamTest {

    @Test
    public void emptyChunk() throws IOException {
        InputStream is = new DataChunkInputStream(Multi.just(DataChunk.create("test".getBytes()), DataChunk.create(new byte[0])));
        int c;
        StringBuilder sb = new StringBuilder();
        while ((c = is.read()) != -1) {
            sb.append((char) c);
        }
        assertThat(sb.toString(), is("test"));
    }

    @Test
    public void differentThreads() throws Exception {
        List<String> test_data = List.of("test0", "test1", "test2", "test3");
        List<String> result = new ArrayList<>();
        OriginThreadPublisher<String, String> pub = new OriginThreadPublisher<>() {
        };

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<?> submitFuture = executorService.submit(() -> {
            for (int i = 0; i < test_data.size(); i++) {
                pub.submit(test_data.get(i));
                sleep();
            }
            pub.complete();
        });
        Future<?> receiveFuture = executorService.submit(() -> {
            DataChunkInputStream chunkInputStream = new DataChunkInputStream(Multi.from(pub)
                    .map(s -> DataChunk.create(s.getBytes())));
            for (int i = 0; i < test_data.size(); i++) {
                try {
                    String token = new String(chunkInputStream.readNBytes(test_data.get(0).length()));
                    System.out.println(">>> " + token);
                    result.add(token);
                } catch (IOException e) {
                    fail(e);
                }
            }
        });

        submitFuture.get(500, TimeUnit.MILLISECONDS);
        receiveFuture.get(500, TimeUnit.MILLISECONDS);
        assertEquals(test_data, result);
    }

    @Test
    public void chunkWith0xFFValue() {
        final byte[] bytes = new byte[]{
                0, 1, 2, 3, 4, 5, 6, (byte) 0xFF, 7, 8, 9, 10
        };
        InputStream is = new DataChunkInputStream(
                new DataChunkPublisher(
                        new DataChunk[]{DataChunk.create(bytes)}));
        try {
            byte[] readBytes = new byte[bytes.length];
            is.read(readBytes);
            if (!Arrays.equals(bytes, readBytes)) {
                fail("expected: " + Arrays.toString(bytes)
                        + ", actual: " + Arrays.toString(readBytes));
            }
        } catch (IOException ex) {
            fail(ex);
        }
    }

    static class DataChunkPublisher implements Publisher<DataChunk> {

        private final DataChunk[] chunks;
        private int delivered = 0;

        public DataChunkPublisher(DataChunk[] chunks) {
            this.chunks = chunks;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    for (; n > 0 && delivered < chunks.length; delivered++, n--) {
                        subscriber.onNext(chunks[delivered]);
                    }
                    if (delivered == chunks.length) {
                        subscriber.onComplete();
                    }
                }
                @Override
                public void cancel() {
                }
            });
        }
    }

    static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

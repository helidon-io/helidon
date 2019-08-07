/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.Flow.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.helidon.media.common.PublisherInputStream}.
 */
public class PublisherInputStreamTest {

    @Test
    public void chunkWith0xFFValue() {
        final byte[] bytes = new byte[]{
            0, 1, 2, 3, 4, 5, 6, (byte) 0xFF, 7, 8, 9, 10
        };
        InputStream is = new PublisherInputStream(
                new DataChunkPublisher(
                        new DataChunk[]{DataChunk.create(bytes)}));
        try {
            byte[] readBytes = new byte[bytes.length];
            is.read(readBytes);
            if (!Arrays.equals(bytes, readBytes)) {
                Assertions.fail("expected: " + Arrays.toString(bytes)
                        + ", actual: " + Arrays.toString(readBytes));
            }
        } catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    static class DataChunkPublisher implements Publisher<DataChunk> {

        private final DataChunk[] chunks;
        private volatile int delivered = 0;

        public DataChunkPublisher(DataChunk[] chunks) {
            this.chunks = chunks;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if(n > 0){
                        for(; delivered < n && delivered < chunks.length
                                ; delivered ++){
                            subscriber.onNext(chunks[delivered]);
                        }
                        if(delivered == chunks.length){
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                }
            });
        }
    }
}

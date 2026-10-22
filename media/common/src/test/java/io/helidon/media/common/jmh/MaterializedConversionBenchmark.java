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
package io.helidon.media.common.jmh;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class MaterializedConversionBenchmark {
    private static final GenericType<byte[]> BYTES = GenericType.create(byte[].class);
    private static final GenericType<String> STRING = GenericType.create(String.class);
    private static final MessageBodyReader<String> CUSTOM_READER = new CustomReader();

    @Param({"128", "4096"})
    public int payloadSize;

    private byte[] payload;
    private MessageBodyReaderContext readerContext;

    @Setup(Level.Trial)
    public void setUp() {
        payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 'a');
        readerContext = MediaContext.create().readerContext();
    }

    @Benchmark
    public byte[] asByteArray() throws Exception {
        return readerContext.unmarshall(Multi.just(DataChunk.create(payload)), BYTES).get();
    }

    @Benchmark
    public String asString() throws Exception {
        return readerContext.unmarshall(Multi.just(DataChunk.create(payload)), STRING).get();
    }

    @Benchmark
    public String asCustomReader() throws Exception {
        return readerContext.unmarshall(Multi.just(DataChunk.create(payload)), CUSTOM_READER, STRING).get();
    }

    private static final class CustomReader implements MessageBodyReader<String> {
        @Override
        public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
            return PredicateResult.supports(String.class, type);
        }

        @Override
        public <U extends String> Single<U> read(Publisher<DataChunk> chunks,
                                                 GenericType<U> type,
                                                 MessageBodyReaderContext context) {
            return ContentReaders.readString(chunks, StandardCharsets.UTF_8).map(type::cast);
        }
    }
}

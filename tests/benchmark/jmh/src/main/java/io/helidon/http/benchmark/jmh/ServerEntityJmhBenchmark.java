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

package io.helidon.http.benchmark.jmh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webserver.http.ServerRequestEntity;
import io.helidon.webserver.http2.Http2ServerRequestEntity;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

/**
 * Measures actual HTTP/1 and HTTP/2 server entity construction, decoding, reading, and cleanup.
 * Materializing reads use the String media reader, which closes its decoded stream.
 * Streaming reads close the exact decoded stream they consume. Both paths explicitly complete raw transport consumption.
 * The same benchmark can run against server artifacts before and after a raw-stream ownership change.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(MICROSECONDS)
public class ServerEntityJmhBenchmark {
    private static final long MAX_BUFFERED_ENTITY_SIZE = 1024 * 1024;
    private static final OutputStream DISCARD = OutputStream.nullOutputStream();
    private static final ServerRequestHeaders HEADERS = ServerRequestHeaders.create(WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8"));
    private static final MediaContext MEDIA_CONTEXT = MediaContext.builder()
            .mediaSupportsDiscoverServices(false)
            .build();

    @Param({"HTTP1", "HTTP2"})
    private String protocol;

    @Param({"identity", "gzip"})
    private String encoding;

    @Param({"1024", "65536"})
    private int bytes;

    private byte[] wirePayload;
    private ContentDecoder decoder;

    /**
     * Create deterministic printable-ASCII content and encode its wire representation outside the measured operations.
     * Verify both measured paths return complete content and signal raw EOF exactly once.
     */
    @Setup(Level.Trial)
    public void setup() {
        byte[] payload = new byte[bytes];
        var random = new SplittableRandom(0x5EED0412L ^ bytes);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) random.nextInt(32, 127);
        }
        switch (encoding) {
            case "identity" -> {
                wirePayload = payload;
                decoder = ContentDecoder.NO_OP;
            }
            case "gzip" -> {
                var compressed = new ByteArrayOutputStream();
                try (var output = new GZIPOutputStream(compressed)) {
                    output.write(payload);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                wirePayload = compressed.toByteArray();
                decoder = stream -> {
                    try {
                        return new GZIPInputStream(stream);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                };
            }
            default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
        }

        var materializedSource = new Source(wirePayload);
        String result = readMaterialized(materializedSource);
        if (!new String(payload, StandardCharsets.US_ASCII).equals(result) || materializedSource.completions != 1) {
            throw new IllegalStateException("Materialized server entity did not return complete content and reach raw EOF");
        }
        var streamingSource = new Source(wirePayload);
        if (readStreaming(streamingSource) != payload.length || streamingSource.completions != 1) {
            throw new IllegalStateException("Streaming server entity did not return complete content and reach raw EOF");
        }
    }

    /**
     * Construct and materialize a server entity through its actual String reader and close its decoded stream.
     *
     * @return materialized request body
     */
    @Benchmark
    public String materialized() {
        return readMaterialized(new Source(wirePayload));
    }

    /**
     * Construct a server entity, consume its decoded stream incrementally, and close that stream.
     *
     * @return number of decoded bytes consumed
     */
    @Benchmark
    public long streaming() {
        return readStreaming(new Source(wirePayload));
    }

    private String readMaterialized(Source source) {
        ReadableEntity entity = entity(source);
        try {
            return entity.as(String.class);
        } finally {
            entity.consume();
        }
    }

    private long readStreaming(Source source) {
        ReadableEntity entity = entity(source);
        try (InputStream stream = entity.inputStream()) {
            return stream.transferTo(DISCARD);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            entity.consume();
        }
    }

    private ReadableEntity entity(Source source) {
        return switch (protocol) {
            case "HTTP1" -> ServerRequestEntity.create(ignored -> { },
                                                       UnaryOperator.identity(),
                                                       decoder,
                                                       source,
                                                       source,
                                                       HEADERS,
                                                       MEDIA_CONTEXT,
                                                       -1,
                                                       MAX_BUFFERED_ENTITY_SIZE);
            case "HTTP2" -> Http2ServerRequestEntity.create(UnaryOperator.identity(),
                                                            decoder,
                                                            source,
                                                            source,
                                                            HEADERS,
                                                            MEDIA_CONTEXT,
                                                            -1,
                                                            MAX_BUFFERED_ENTITY_SIZE);
            default -> throw new IllegalArgumentException("Unexpected protocol: " + protocol);
        };
    }

    private static final class Source implements Function<Integer, BufferData>, Runnable {
        private final byte[] payload;
        private boolean sent;
        private int completions;

        private Source(byte[] payload) {
            this.payload = payload;
        }

        @Override
        public BufferData apply(Integer estimate) {
            if (sent) {
                return BufferData.empty();
            }
            sent = true;
            return BufferData.create(payload);
        }

        @Override
        public void run() {
            completions++;
        }
    }
}

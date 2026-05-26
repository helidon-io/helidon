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

package io.helidon.webserver.benchmark.jmh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.http.media.json.binding.JsonBindingSupport;
import io.helidon.http.media.json.smile.SmileSupport;
import io.helidon.http.media.jsonb.JsonbSupport;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class MediaWriterJmhTest {
    private static final GenericType<Map<String, String>> MESSAGE_TYPE = new GenericType<>() { };
    private static final int SMALL_PAYLOAD_SIZE = 128;
    private static final int LARGE_PAYLOAD_SIZE = 196_608;

    @Param({"json-binding", "jsonb", "jackson", "smile"})
    private String media;

    @Param({"small", "large"})
    private String payloadSize;

    private EntityWriter<Map<String, String>> writer;
    private Map<String, String> message;

    @Setup
    public void setup() {
        MediaSupport support = mediaSupport();
        support.init(MediaContext.create());

        writer = support.writer(MESSAGE_TYPE, WritableHeaders.create()).supplier().get();
        message = Map.of("framework", "json-binding",
                         "media", media,
                         "text", "a".repeat(payloadSize()),
                         "index", "42");
    }

    @Benchmark
    public void writeToStream(Blackhole bh) {
        writer.write(MESSAGE_TYPE, message, new BlackholeOutputStream(bh), WritableHeaders.create());
    }

    @Benchmark
    public void instanceBytes(Blackhole bh) {
        bh.consume(writer.instanceWriter(MESSAGE_TYPE, message, WritableHeaders.create())
                           .instanceBytes());
    }

    private MediaSupport mediaSupport() {
        return switch (media) {
            case "json-binding" -> JsonBindingSupport.create();
            case "jsonb" -> JsonbSupport.create();
            case "jackson" -> JacksonSupport.create();
            case "smile" -> SmileSupport.create();
            default -> throw new IllegalArgumentException("Unsupported media: " + media);
        };
    }

    private int payloadSize() {
        return switch (payloadSize) {
            case "small" -> SMALL_PAYLOAD_SIZE;
            case "large" -> LARGE_PAYLOAD_SIZE;
            default -> throw new IllegalArgumentException("Unsupported payload size: " + payloadSize);
        };
    }

    private static final class BlackholeOutputStream extends OutputStream {
        private final Blackhole blackhole;
        private long bytes;

        private BlackholeOutputStream(Blackhole blackhole) {
            this.blackhole = blackhole;
        }

        @Override
        public void write(int b) throws IOException {
            bytes++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            bytes += len;
        }

        @Override
        public void close() throws IOException {
            blackhole.consume(bytes);
        }
    }
}
